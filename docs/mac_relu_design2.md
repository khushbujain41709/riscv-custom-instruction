# Manual Encoding Walkthrough & End-to-End Testing

## Manually Encoding `mac_relu x10, x5, x6`

We will encode:

```
mac_relu x10, x5, x6
```

Using our locked R-type format:

```
funct7 | rs2 | rs1 | funct3 | rd | opcode
```

### Step 1: Convert Each Register to 5-bit Binary

| Register | 5-bit Binary |
|----------|--------------|
| `x10` | `01010` |
| `x5` | `00101` |
| `x6` | `00110` |

### Step 2: Place Fields into the 32-bit Format

```
31        25 24    20 19    15 14   12 11     7 6      0
+-----------+--------+--------+-------+--------+---------+
| 0000001   | 00110  | 00101  |  000  | 01010  | 0001011 |
+-----------+--------+--------+-------+--------+---------+
   funct7      rs2      rs1     funct3   rd       opcode
```

### Step 3: Concatenate the Bits

```
00000010011000101000010100001011
```

### Step 4: Group into 4-bit Nibbles

```
0000 0010 0110 0010 1000 0101 0000 1011
```

### Step 5: Convert Each Nibble to Hex

| Binary | Hex |
|--------|-----|
| `0000` | `0` |
| `0010` | `2` |
| `0110` | `6` |
| `0010` | `2` |
| `1000` | `8` |
| `0101` | `5` |
| `0000` | `0` |
| `1011` | `B` |

### Final Machine Code

```
0x0262850B
```

---

## Toolchain Setup Recap

### 1. Clone `riscv-opcodes`

```bash
git clone https://github.com/riscv/riscv-opcodes.git
cd riscv-opcodes
```

### 2. Create Our Custom Extension File

Inside the repository, create:

```
extensions/rv32_xmac
```

> The `X` convention is appropriate for a non-standard/custom extension.

### 3. Add Our Instruction Definition

```
mac_relu rd rs1 rs2 31..25=0x01 14..12=0x0 6..0=0x0b
```

| Field | Meaning |
|-------|---------|
| `mac_relu` | Instruction name |
| `rd` | Destination register |
| `rs1` | Source register 1 |
| `rs2` | Source register 2 |
| `31..25=0x01` | funct7 = `0000001` |
| `14..12=0x0` | funct3 = `000` |
| `6..0=0x0b` | opcode = `0001011` |

> Adding the instruction to `riscv-opcodes` alone does **not** automatically make your already-installed GCC/binutils understand `mac_relu`. Binutils/GCC modifications (see toolchain integration section) are still required.

---

## End-to-End Test: Assemble, Disassemble, Execute

### 1. Write Assembly

```asm
li x10, 5
li x5, 3
li x6, 4
mac_relu x10, x5, x6
```

### 2. Assemble It

```bash
riscv32-unknown-elf-as test.S -o test.o
```

### 3. Check Using Objdump

```bash
riscv32-unknown-elf-objdump -d test.o
```

**Expected output:**

```
00000000 <...>:
   ...
   mac_relu x10, x5, x6
```

### 4. Run It on Modified Spike

**Initial register values:**

| Register | Value |
|----------|-------|
| `x10` | 5 |
| `x5` | 3 |
| `x6` | 4 |

**Execution:**

```
x10 = max(0, 5 + 3 × 4) = 17
```

**Final result:**

```
x10 = 17
```

---

## Verification Pipeline

```
Assembly text
mac_relu x10, x5, x6
        ↓
   Assembler
        ↓
  Machine code
        ↓
    objdump
        ↓
mac_relu x10, x5, x6
```

### Encoding Summary

| Representation | Value |
|-----------------|-------|
| Assembly | `MAC_RELU x10, x5, x6` |
| Binary | `00000010011000101000010100001011` |
| Hex | `0x0262850B` |

---

## Full Project Flow

```
Step 1–5: Design instruction
        ↓
Step 6: Calculate encoding
        ↓
Step 7: Add MAC_RELU to RISC-V opcode definitions
        ↓
Step 8: Build/enable assembler support
        ↓
Step 9: Compile assembly program
        ↓
Step 10: Run objdump
        ↓
    Verify:
mac_relu x10, x5, x6
        ↓
Step 11: Implement semantics in Spike
        ↓
Step 12: Execute and test
```