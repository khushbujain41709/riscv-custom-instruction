# Custom RISC-V ISA Extension: MAC-RELU

## Idea Behind the Project

**ML knowledge tells us:**
> Neural networks perform huge numbers of matrix multiplications, dot products, activations, and quantization operations.

**Computer architecture knowledge tells us:**
> Which of these operations are repeated enough that special hardware could accelerate them?

CPUs don't just spend time doing arithmetic. They also spend time:
- Loading data
- Storing data
- Moving data between registers
- Executing instructions

For ML workloads, **data movement can be extremely expensive**.

---

## Our Instruction Syntax

```
MAC_RELU rd, rs1, rs2
rd = max(0, rd + rs1 × rs2)
```

32-bit R-type instruction format.

---

## Step-by-Step Roadmap

| Step | Task | Description |
|------|------|-------------|
| 1 | Define the exact operation | Decide mathematically what MAC-RELU does |
| 2 | Choose operands/registers | Decide what `rd`, `rs1`, and `rs2` represent |
| 3 | Choose the data type | e.g., INT8 × INT8 → INT32 |
| 4 | Define instruction syntax | `mac_relu rd, rs1, rs2` |
| 5 | Define instruction semantics | `rd = ReLU(rd + rs1 × rs2)` |
| 6 | Choose a RISC-V instruction format | Most likely an R-type custom instruction |
| 7 | Design the 32-bit encoding | `opcode + funct3 + funct7 + rd + rs1 + rs2` |
| 8 | Implement it in Spike | Add decode + execution behavior |
| 9 | Write assembly/C tests | Compare normal MUL + ADD + ReLU against MAC_RELU |
| 10 | Benchmark and analyze results | Compare performance metrics |

---

## Define the Operands and Register Behavior

| Register | Meaning |
|----------|---------|
| `rs1` | Input value |
| `rs2` | Weight value |
| `rd` | Accumulator **and** destination |

### Internal Data Flow

```
    rs1 ──┐
          ×───┐
    rs2 ──┘   │
              + ─── ReLU ───→ rd
    old rd ───┘
```

- **MAC** → accumulate multiplication result
- **ReLU** → clamp negative result to zero

---

## Why Use `rd` as Both Accumulator and Destination?

Because this matches the accumulate idea:

```
old rd + rs1 × rs2 → new rd
```

This means a loop can repeatedly accumulate results:

```
acc = acc + x1*w1
acc = acc + x2*w2
acc = acc + x3*w3
...
output = ReLU(acc + bias)
```

You repeatedly perform:
```
acc += xi * wi
```
Then:
```
acc += bias
```
Then:
```
output = ReLU(acc)
```

### Extended Instruction Idea

```
MAC_RELU rd, rs1, rs2, bias
rd = ReLU(rd + rs1*rs2 + bias)
```

---

## Baseline vs Custom ISA

### Baseline: Normal RISC-V Instructions

```
LOAD
LOAD
MUL
ADD
MUL
ADD
...
```

### Custom ISA

```
LOAD
LOAD
INT8_DOT
INT8_DOT
...
```

### Metrics to Measure

| Metric | Description |
|--------|-------------|
| Instruction count | Number of instructions executed |
| Cycle count | Number of CPU cycles taken |
| Execution time | Wall-clock/simulated execution time |
| Code size | Size of the compiled binary |

**Speedup formula:**

```
Speedup = Baseline cycles / Custom cycles
```

---

## R-Type Instruction Format

```
31        25 24    20 19    15 14   12 11     7 6      0
+-----------+--------+--------+-------+--------+---------+
|  funct7   |  rs2   |  rs1   |funct3 |   rd   | opcode  |
+-----------+--------+--------+-------+--------+---------+
```

### For Our Instruction

```
31        25 24    20 19    15 14   12 11     7 6      0
+-----------+--------+--------+-------+--------+---------+
|  funct7   |  rs2   |  rs1   |funct3 |   rd   | CUSTOM  |
+-----------+--------+--------+-------+--------+---------+
```

---

## Encoding Fields

### Choosing a Custom Opcode

RISC-V provides dedicated opcode spaces for custom instructions. For our project, we'll use **custom-0**.

For a 32-bit instruction:

```
opcode = 0001011
```

This is the **custom-0** major opcode.

### Choosing funct3

We only have one instruction right now, so we will choose:

```
funct3 = 000
```

### Choosing funct7

```
funct7 = 0000001
```

---

## Final Encoding

```
31        25 24    20 19    15 14   12 11     7 6      0
+-----------+--------+--------+-------+--------+---------+
| 0000001   |  rs2   |  rs1   |  000  |   rd   | 0001011 |
+-----------+--------+--------+-------+--------+---------+
   funct7      rs2      rs1     funct3    rd      custom-0
```

| Field | Value | Bits |
|-------|-------|------|
| funct7 | `0000001` | [31:25] |
| rs2 | register | [24:20] |
| rs1 | register | [19:15] |
| funct3 | `000` | [14:12] |
| rd | register | [11:7] |
| opcode | `0001011` (custom-0) | [6:0] |

> `funct3 = 000` and `funct7 = 0000001` are values assigned by our custom extension.