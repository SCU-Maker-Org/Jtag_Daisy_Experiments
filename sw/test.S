# Simple test program for Ibex cores in JTAG daisy chain
# This program runs an infinite loop incrementing a counter in memory

.section .text
.globl _start

_start:
    # Initialize stack pointer (top of RAM)
    li sp, 0x0010FFFC
    
    # Initialize counter at 0x00100100
    li t0, 0x00100100
    li t1, 0
    sw t1, 0(t0)
    
loop:
    # Increment counter
    lw t1, 0(t0)
    addi t1, t1, 1
    sw t1, 0(t0)
    
    # Small delay
    li t2, 100
delay:
    addi t2, t2, -1
    bnez t2, delay
    
    # Loop forever
    j loop
