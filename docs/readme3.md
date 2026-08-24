# Toolchain Integration: Teaching Binutils About `mac_relu`

Adding an instruction to `riscv-opcodes` alone does **not** make an already-installed GCC/binutils understand `mac_relu`. This section documents the full toolchain integration process.

---

## 1. Clone `riscv-opcodes`

```bash
git clone https://github.com/riscv/riscv-opcodes.git
cd riscv-opcodes
```

---

## 2. Create Our Custom Extension File

Inside the repository, create:

```
extensions/rv32_xmac
```

> The `X` prefix convention is appropriate for a non-standard/custom extension.

---

## 3. Add Our Instruction Definition

Add this line to the extension file:

```
mac_relu rd rs1 rs2 31..25=0x01 14..12=0x0 6..0=0x0b
```

### Breaking It Down

| Field | Meaning |
|-------|---------|
| `mac_relu` | Instruction name |
| `rd` | Destination register |
| `rs1` | Source register 1 |
| `rs2` | Source register 2 |
| `31..25=0x01` | funct7 = `0000001` |
| `14..12=0x0` | funct3 = `000` |
| `6..0=0x0b` | opcode = `0001011` |

---

## 4. Get the RISC-V Binutils Source

Clone the RISC-V GNU toolchain (which includes binutils as a submodule):

```bash
git clone https://github.com/riscv-collab/riscv-gnu-toolchain.git
cd riscv-gnu-toolchain
git submodule update --init --recursive
```

### Relevant File Locations

```
riscv-gnu-toolchain/
└── riscv-binutils/
    ├── include/
    │   └── opcode/
    │       └── riscv-opc.h
    │
    └── opcodes/
        └── riscv-opc.c
```

The key file for instruction definitions is generally:

```
opcodes/riscv-opc.c
```

---

## 5. Add Instruction Definition in `riscv-opc.c`

Inside the RISC-V opcode table, add an entry conceptually like:

```c
{"mac_relu", 0, INSN_CLASS_I, "d,s,t",
 MATCH_MAC_RELU, MASK_MAC_RELU,
 match_opcode, 0},
```

### What This Tells Binutils

| Field | Meaning |
|-------|---------|
| `mac_relu` | Instruction mnemonic |
| `d,s,t` | Operand format → `rd, rs1, rs2` |
| `MATCH_MAC_RELU` | Fixed bits identifying the instruction |
| `MASK_MAC_RELU` | Which bits must match |

---

## 6. Define `MATCH` and `MASK` in `riscv-opc.h`

```c
#define MATCH_MAC_RELU 0x0200000b
#define MASK_MAC_RELU  0xfe00707f
```

MATCH is the fixed pattern of bits that identifies your custom instruction. It tells the assembler: "If you see these bits in these positions, it's my instruction!"<br>
The Formula:<br>
MATCH = (funct7 << 25) | (funct3 << 12) | opcode<br>
<br>

What is MASK?<br>
MASK tells the assembler which bits to check (and which bits to ignore).<br>
MASK = (0x7F << 25) | (0x07 << 12) | 0x7F<br>
<br>

The MASK has 1s where the MATCH has fixed bits:<br>
Bits 31-25 (funct7): 1111111 = 0x7F<br>
Bits 14-12 (funct3): 111 = 0x07<br>
Bits 6-0 (opcode): 1111111 = 0x7F<br>
All other bits (rs1, rs2, rd) have 0 in the MASK (ignored).<br>
<br>

### Assembler / Disassembler Flow

**Assembler:**
```
mac_relu x10, x5, x6
        ↓
0x0262850B
```

**Objdump (disassembly):**
```
0x0262850B
        ↓
mac_relu x10, x5, x6
```

---

## 7. Understanding `MATCH_MAC_RELU`

`MATCH` contains **only the fixed fields**: `funct7`, `funct3`, `opcode`.
It does **not** include `rd`, `rs1`, `rs2` — those change for every instruction instance.

### Field Encoding

| Field | Value |
|-------|-------|
| funct7 | `0000001` |
| funct3 | `000` |
| opcode | `0001011` |

### Calculating the Match Value

```
funct7 = 1  →  1 << 25 = 0x02000000
opcode = 0x0B
```

Therefore:

```
0x02000000 | 0x0000000B = 0x0200000B
```

```c
#define MATCH_MAC_RELU 0x0200000b
```

---

## 8. Defining the Mask

The mask should check:

| Bit Range | Field |
|-----------|-------|
| 31–25 | funct7 |
| 14–12 | funct3 |
| 6–0 | opcode |

Conceptually:

```
MASK_MAC_RELU = funct7 mask | funct3 mask | opcode mask
```

Resulting in:

```c
#define MASK_MAC_RELU 0xfe00707f
```

### Visual Breakdown

```
1111111 00000 00000 111 00000 1111111
^^^^^^^             ^^^       ^^^^^^^
funct7              funct3    opcode
```

The variable fields — `rd`, `rs1`, `rs2` — are **masked out** because they can contain any register number.

---

## 9. Rebuild and Test

After rebuilding the modified toolchain, these commands should work:

```bash
riscv32-unknown-elf-as test.S -o test.o
riscv32-unknown-elf-objdump -d test.o
```

---

## Summary of Files Modified

| File | Purpose |
|------|---------|
| `riscv-opcodes/extensions/rv32_xmac` | Defines the instruction at the ISA-spec level |
| `include/opcode/riscv-opc.h` | Defines `MATCH_MAC_RELU` and `MASK_MAC_RELU` |
| `opcodes/riscv-opc.c` | Registers `mac_relu` in the opcode table for assembler/disassembler |