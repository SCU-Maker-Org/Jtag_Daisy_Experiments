BUILD_DIR = ./build

PRJ = playground

test:
	mill -i $(PRJ).test

verilog:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mill -i $(PRJ).runMain Elaborate --target-dir $(BUILD_DIR)

help:
	mill -i $(PRJ).runMain Elaborate --help

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: test verilog help reformat checkformat clean

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by yourself."

# ==========================================
# JTAG Simulation Targets
# ==========================================
VERILATOR = verilator
CPP_SRCS = csrc/sim_main.cpp
GEN_DIR = ./generated
TOP_MODULE = TopMain

jtag-verilog:
	mkdir -p $(GEN_DIR)
	mill -i $(PRJ).runMain Elaborate --target-dir $(GEN_DIR)

jtag-sim: jtag-verilog
	mkdir -p $(BUILD_DIR)
	$(VERILATOR) --cc --exe --build -j 4 \
		--top-module $(TOP_MODULE) \
		-I$(GEN_DIR) \
		$(GEN_DIR)/$(TOP_MODULE).sv \
		$(CPP_SRCS) \
		--Mdir $(BUILD_DIR) \
		-o sim_top

run-jtag: jtag-sim
	$(BUILD_DIR)/sim_top

run-only-jtag:
	@echo "[Warn] Can only be used after `make jtag-sim`"
	$(BUILD_DIR)/sim_top

-include ../Makefile
