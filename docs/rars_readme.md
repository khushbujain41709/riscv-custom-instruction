# Executing `mac_relu` in RARS

This document covers the second half of the project: making the custom `mac_relu` instruction **actually execute**, not just be recognized by an assembler. It picks up where the Binutils work (see main `README.md`) left off.

---

#### rars.jar in RISC-V is unmodified and rars.jar in rars folder is modified.

## Table of Contents
1. [Why This Part Was Needed](#why-this-part-was-needed)
2. [What Was Achieved](#what-was-achieved)
3. [Background: How RARS Loads Instructions](#background-how-rars-loads-instructions)
4. [Implementation](#implementation)
5. [First Attempt — What Went Wrong](#first-attempt--what-went-wrong)
6. [Corrected Implementation](#corrected-implementation)
7. [Build Process](#build-process)
8. [Build Issue — `jar: command not found`](#build-issue--jar-command-not-found)
9. [Test Program](#test-program)
10. [Result](#result)
11. [Verification Table](#verification-table)
12. [Commands Reference](#commands-reference)
13. [Key Takeaways](#key-takeaways)

---

## Why This Part Was Needed

Modifying GNU Binutils (`riscv-opc.c` / `riscv-opc.h`) taught the **assembler and disassembler** to recognize `mac_relu` as a valid mnemonic and correctly encode/decode it to/from `0x0262850B`. That was necessary but not sufficient — an assembler only maps text ↔ bits, it does not run anything.

No simulator or CPU automatically knows what a custom opcode is supposed to *do*. RARS is a fixed Java simulator that implements standard RV32IM; it has zero built-in knowledge of `mac_relu`. Loading `0x0262850B` into an unmodified RARS would either throw an "instruction not supported" error or silently do nothing meaningful — it would **not** correctly compute `ReLU(rd + rs1*rs2)` on its own.

So to honestly claim the instruction "works," execution semantics had to be added somewhere with a real fetch-decode-execute loop. RARS was chosen over Spike/WSL because:
- It's a single `.jar`, no virtualization layer
- No Linux dependency (previous WSL attempts hit hash-mismatch/corruption issues)
- Pure Java build — no cross-compilation toolchain needed

---

## What Was Achieved

- Modified RARS's own source to recognize the `mac_relu` mnemonic **natively** (its own internal assembler, separate from GNU Binutils)
- Implemented real execution semantics: `rd = max(0, rd + rs1 * rs2)`
- Rebuilt `rars.jar` from source
- Ran a multi-case test program using **plain `mac_relu` text syntax** (no `.4byte` hex workaround needed anymore)
- Got correct, verifiable numeric output matching hand-calculated expected values

---

## Background: How RARS Loads Instructions

RARS auto-discovers instructions: at startup it scans the `rars/riscv/instructions/` package for any class extending `BasicInstruction` and adds it to its instruction table. This means **no manual registration file needs to be edited** — dropping a correctly-written `.java` file into that folder is enough.

Each `BasicInstruction` subclass supplies two things in one place:
1. A **usage string + 32-bit mask template** (using `f`/`s`/`t` for operand bit positions: `f` = first operand = `rd`, `s` = second operand = `rs1`, `t` = third operand = `rs2`) — this is what teaches RARS's *own* parser the syntax.
2. A **`simulate()` method** — this is what actually executes when the instruction runs.

This was confirmed by reading RARS's real source (`MUL.java`, `Arithmetic.java`, `BasicInstruction.java`) before writing the custom class.

---

## Implementation

New file: `rars/src/rars/riscv/instructions/MAC_RELU.java`

The encoding mask mirrors the same bit layout already used in the Binutils work:

```
0000001 ttttt sssss 000 fffff 0001011
  ↑       ↑     ↑    ↑    ↑      ↑
funct7   rs2   rs1  fn3   rd   opcode (custom-0)
```

---

## First Attempt — What Went Wrong

![VS Code showing unresolved import errors](images/vscode-import-error.png)

The first version extended RARS's `Arithmetic` base class (the same class `MUL.java` extends), since it looked like a ready-made template:

```java
public class MAC_RELU extends Arithmetic {
    public MAC_RELU() {
        super("mac_relu t1,t2,t3", "...", "0000001", "000");
    }
    public long compute(long value, long value2) { return 0; } // unused
    // simulate() overridden separately
}
```

Two problems surfaced:

1. **Missing imports (the VS Code error shown above)** — `RegisterFile` and `ProgramStatement` were used but never imported, giving `"RegisterFile cannot be resolved"` / `"ProgramStatement cannot be resolved to a type"`.

2. **A silent, more serious bug** — `Arithmetic`'s constructor hardcodes the standard opcode:
   ```java
   public Arithmetic(String usage, String description, String funct7, String funct3) {
       super(usage, description, BasicInstructionFormat.R_FORMAT,
               funct7 + " ttttt sssss " + funct3 + " fffff 0110011");
   }
   ```
   `0110011` is the **standard R-type opcode** (used by `add`, `mul`, `sub`, etc.), not the custom-0 opcode `0001011` this project needs. `Arithmetic` gives no way to override it. Extending `Arithmetic` would have silently encoded `mac_relu` with the wrong opcode — it would never have matched the `0x0262850B` encoding produced by the modified GNU assembler.

**Fix:** extend `BasicInstruction` directly instead, which allows full control over the opcode field.

---

## Corrected Implementation

```java
package rars.riscv.instructions;

import rars.ProgramStatement;
import rars.riscv.BasicInstruction;
import rars.riscv.BasicInstructionFormat;
import rars.riscv.hardware.RegisterFile;

/**
 * Custom instruction: mac_relu rd, rs1, rs2
 * Operation: rd = max(0, rd + rs1 * rs2)
 *
 * Encoding (R-type, custom-0 opcode space):
 *   funct7 = 0000001   [31:25]
 *   rs2               [24:20]
 *   rs1               [19:15]
 *   funct3 = 000       [14:12]
 *   rd                [11:7]
 *   opcode = 0001011   [6:0]   (custom-0)
 */
public class MAC_RELU extends BasicInstruction {
    public MAC_RELU() {
        super("mac_relu t1,t2,t3",
                "Multiply-Accumulate with ReLU: set t1 to max(0, t1 + t2*t3)",
                BasicInstructionFormat.R_FORMAT,
                "0000001 ttttt sssss 000 fffff 0001011");
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        // operands[0] = rd (first operand, "f")
        // operands[1] = rs1 (second operand, "s")
        // operands[2] = rs2 (third operand, "t")
        int rdVal = RegisterFile.getValue(operands[0]);
        int rs1Val = RegisterFile.getValue(operands[1]);
        int rs2Val = RegisterFile.getValue(operands[2]);

        int result = rdVal + rs1Val * rs2Val;
        if (result < 0) {
            result = 0;
        }
        RegisterFile.updateRegister(operands[0], result);
    }
}
```

Unlike `MUL` (which only needs `rs1`/`rs2` values), `mac_relu` needs `rd`'s **existing** value too — since it accumulates into it. This is why `RegisterFile.getValue(operands[0])` is read explicitly *before* the register is overwritten, rather than relying on the `Arithmetic` base class's `compute(value, value2)` signature, which only ever receives `rs1` and `rs2`.

---

## Build Process

```bash
cd /d/RISC-V
git clone https://github.com/TheThirdOne/rars.git
cd rars
git submodule update --init --recursive

# Place MAC_RELU.java at:
# src/rars/riscv/instructions/MAC_RELU.java

./build-jar.sh
```

`build-jar.sh` simply compiles every `.java` file with `javac` and packages the result with `jar` — no Gradle or Ant involved.

---

## Build Issue — `jar: command not found`

`javac` succeeded, but the script failed at the packaging step:

```
./build-jar.sh: line 14: jar: command not found
```

**Cause:** MSYS2's `PATH` was pointing to a **JRE** (Java Runtime — can run `.jar` files) rather than a full **JDK** (Java Development Kit — has `javac`, `jar`, etc.). `jar.exe` simply wasn't reachable.

**Diagnosis:**
```bash
find /c -name "jar.exe" 2>/dev/null
```
This located a real JDK at `/c/Program Files/Java/jdk-22/bin/jar.exe`.

**Immediate fix (one-off):**
```bash
/c/Program\ Files/Java/jdk-22/bin/jar cfm rars.jar META-INF/MANIFEST.MF -C build .
```

**Permanent fix** — add to MSYS2's `~/.bashrc` so every new terminal has the full JDK on `PATH`:
```bash
export PATH="/c/Program Files/Java/jdk-22/bin:$PATH"
```

---

## Test Program

`test_mac_relu.S` — note this uses the **plain `mac_relu` mnemonic directly**, since RARS's own internal assembler now understands it (no `.4byte` raw hex workaround needed, unlike the earlier RARS testing done before this instruction was added to RARS's source):

```asm
.text
.globl main
main:
    li x10, -2
    li x5, 3
    li x6, 4
    mac_relu x10, x5, x6
    li a7, 1
    mv a0, x10
    ecall
    li a7, 11
    li a0, 10
    ecall

    li x10, -5
    li x5, 2
    li x6, 1
    mac_relu x10, x5, x6
    li a7, 1
    mv a0, x10
    ecall
    li a7, 11
    li a0, 10
    ecall

    li x10, 0
    li x5, 10
    li x6, 5
    mac_relu x10, x5, x6
    li a7, 1
    mv a0, x10
    ecall
    li a7, 11
    li a0, 10
    ecall

    li x10, 0
    li x5, 0
    li x6, 0
    mac_relu x10, x5, x6
    li a7, 1
    mv a0, x10
    ecall
    li a7, 10
    ecall
```

Run:
```bash
cd /d/RISC-V
java -jar rars/rars.jar test_mac_relu.S
```
![rars modified output](images\rars_modified.png)

---

## Result

```
RARS 1.6  Copyright 2003-2019 Pete Sanderson and Kenneth Vollmar

10
0
50
0
```

---

## Verification Table

| Call | rd (before) | rs1 | rs2 | rd + rs1×rs2 | ReLU(x) = max(0, x) | RARS Output |
|------|-------------|-----|-----|--------------|----------------------|-------------|
| 1    | -2          | 3   | 4   | -2 + 12 = 10 | 10                   | **10**    |
| 2    | -5          | 2   | 1   | -5 + 2 = -3  | 0                    | **0**     |
| 3    | 0           | 10  | 5   | 0 + 50 = 50  | 50                   | **50**    |
| 4    | 0           | 0   | 0   | 0 + 0 = 0    | 0                    | **0**     |

All four outputs match the independently hand-calculated expected values exactly.

---

## Commands Reference

```bash
# Clone RARS
cd /d/RISC-V
git clone https://github.com/TheThirdOne/rars.git
cd rars
git submodule update --init --recursive

# Fix PATH permanently (add to ~/.bashrc)
export PATH="/c/Program Files/Java/jdk-22/bin:$PATH"

# Build
./build-jar.sh

# If jar is not found, use full path once:
/c/Program\ Files/Java/jdk-22/bin/jar cfm rars.jar META-INF/MANIFEST.MF -C build .

# Run test program
cd /d/RISC-V
java -jar rars/rars.jar test_mac_relu.S

# Run RARS GUI
java -jar rars/rars.jar
```

---

## Key Takeaways

### What Worked
1. RARS's auto-discovery mechanism — only one new file needed, no registry edits
2. Extending `BasicInstruction` directly to gain full control of the opcode field
3. Reading `rd`'s pre-existing value manually in `simulate()`, since accumulate-style instructions need it and `Arithmetic`'s `compute()` signature doesn't provide it
4. Building via `javac` + `jar` directly — no Gradle/Ant setup required

### What Didn't Work (and why)
1. **Extending `Arithmetic`** — silently forces the standard opcode `0110011`, incompatible with the custom-0 opcode `0001011` this project needs
2. **`./build-jar.sh` out of the box** — MSYS2's default `PATH` pointed at a JRE, not a full JDK, so `jar.exe` wasn't found

### What This Proves
This closes the full loop the project set out to demonstrate:

**Design → Encode (GNU Binutils) → Decode (GNU objdump) → Native assembly (RARS) → Execution (patched RARS)**

The instruction is no longer just a recognized mnemonic — it is a working piece of simulator logic that reads registers, performs the intended multiply-accumulate-then-clamp computation, and writes back a correct result, verified against independently hand-calculated values across four distinct test cases.