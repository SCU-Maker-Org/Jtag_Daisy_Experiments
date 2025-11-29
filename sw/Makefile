# Makefile for building test programs for Ibex

# RISC-V toolchain prefix (nix uses riscv32-none-elf-)
CROSS_COMPILE ?= riscv32-none-elf-

CC = $(CROSS_COMPILE)gcc
AS = $(CROSS_COMPILE)as
LD = $(CROSS_COMPILE)ld
OBJCOPY = $(CROSS_COMPILE)objcopy
OBJDUMP = $(CROSS_COMPILE)objdump

# Flags
ASFLAGS = -march=rv32im -mabi=ilp32
LDFLAGS = -T link.ld -nostdlib

# Targets
all: test.bin test.hex

test.o: test.S
	$(AS) $(ASFLAGS) -o $@ $<

test.elf: test.o link.ld
	$(LD) $(LDFLAGS) -o $@ test.o

test.bin: test.elf
	$(OBJCOPY) -O binary $< $@

test.hex: test.elf
	$(OBJCOPY) -O verilog $< $@

test.dump: test.elf
	$(OBJDUMP) -d $< > $@

clean:
	rm -f *.o *.elf *.bin *.hex *.dump

.PHONY: all clean
