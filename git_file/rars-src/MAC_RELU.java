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