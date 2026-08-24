.text
.globl main
main:
    # Test 1: ReLU(-2 + 3×4) = 10
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

    # Test 2: ReLU(-5 + 2×1) = 0
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

    # Test 3: ReLU(0 + 10×5) = 50
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

    # Test 4: ReLU(0 + 0×0) = 0
    li x10, 0
    li x5, 0
    li x6, 0
    mac_relu x10, x5, x6
    li a7, 1
    mv a0, x10
    ecall

    li a7, 10
    ecall