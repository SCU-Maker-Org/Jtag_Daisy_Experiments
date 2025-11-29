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

# IP paths
IBEX_RTL        := ip/ibex/rtl
IBEX_PRIM       := ip/ibex/vendor/lowrisc_ip/ip/prim/rtl
IBEX_PRIM_GEN   := ip/ibex/vendor/lowrisc_ip/ip/prim_generic/rtl
IBEX_DV_PRIM    := ip/ibex/dv/uvm/core_ibex/common/prim
RISCV_DBG       := ip/riscv-dbg/src
RISCV_DBG_ROM   := ip/riscv-dbg/debug_rom
COMMON_CELLS    := ip/common_cells/src
TECH_CELLS      := ip/tech_cells_generic/src/rtl

# Include paths for Verilator
VINCLUDES := \
	-I$(GEN_DIR) \
	-I$(IBEX_RTL) \
	-I$(RISCV_DBG) \
	-I$(IBEX_PRIM) \
	-Iip/ibex/vendor/lowrisc_ip/dv/sv/dv_utils \
	-Iip/common_cells/include \
	-I$(TECH_CELLS)

# Ibex primitive packages (order matters!)
IBEX_PRIM_SRCS := \
	$(IBEX_PRIM)/prim_util_pkg.sv \
	$(IBEX_PRIM)/prim_mubi_pkg.sv \
	$(IBEX_PRIM)/prim_cipher_pkg.sv \
	$(IBEX_PRIM)/prim_count_pkg.sv \
	$(IBEX_PRIM)/prim_ram_1p_pkg.sv \
	$(IBEX_PRIM)/prim_secded_pkg.sv \
	$(IBEX_DV_PRIM)/prim_pkg.sv \
	$(IBEX_DV_PRIM)/prim_clock_gating.sv \
	$(IBEX_PRIM_GEN)/prim_generic_clock_gating.sv \
	$(IBEX_DV_PRIM)/prim_buf.sv \
	$(IBEX_PRIM_GEN)/prim_generic_buf.sv

# Tech cells
TECH_CELLS_SRCS := \
	$(TECH_CELLS)/tc_clk.sv \
	$(TECH_CELLS)/tc_sram.sv

# Common cells
COMMON_CELLS_SRCS := \
	$(COMMON_CELLS)/deprecated/fifo_v2.sv \
	$(COMMON_CELLS)/fifo_v3.sv \
	$(COMMON_CELLS)/cdc_2phase_clearable.sv \
	$(COMMON_CELLS)/sync.sv \
	$(COMMON_CELLS)/cdc_reset_ctrlr_pkg.sv \
	$(COMMON_CELLS)/cdc_reset_ctrlr.sv \
	$(COMMON_CELLS)/spill_register.sv \
	$(COMMON_CELLS)/spill_register_flushable.sv \
	$(COMMON_CELLS)/stream_register.sv \
	$(COMMON_CELLS)/lzc.sv \
	$(COMMON_CELLS)/rr_arb_tree.sv \
	$(COMMON_CELLS)/popcount.sv \
	$(COMMON_CELLS)/unread.sv \
	$(COMMON_CELLS)/cf_math_pkg.sv \
	$(COMMON_CELLS)/cdc_4phase.sv

# RISC-V Debug Module
RISCV_DBG_SRCS := \
	$(RISCV_DBG_ROM)/debug_rom.sv \
	$(RISCV_DBG)/dm_pkg.sv \
	$(RISCV_DBG)/dm_csrs.sv \
	$(RISCV_DBG)/dm_mem.sv \
	$(RISCV_DBG)/dm_sba.sv \
	$(RISCV_DBG)/dm_obi_top.sv \
	$(RISCV_DBG)/dm_top.sv \
	$(RISCV_DBG)/dmi_jtag.sv \
	$(RISCV_DBG)/dmi_jtag_tap.sv \
	$(RISCV_DBG)/dmi_cdc.sv

# Ibex core
IBEX_SRCS := \
	$(IBEX_RTL)/ibex_pkg.sv \
	$(IBEX_RTL)/ibex_top.sv

# All SV sources in order
VSRCS := \
	$(IBEX_PRIM_SRCS) \
	$(TECH_CELLS_SRCS) \
	$(COMMON_CELLS_SRCS) \
	$(RISCV_DBG_SRCS) \
	$(IBEX_SRCS) \
	$(GEN_DIR)/$(TOP_MODULE).sv

# Verilator warning suppressions
VFLAGS := -Wno-WIDTH -Wno-PINMISSING -Wno-IMPLICIT -Wno-MODDUP -Wno-UNOPTFLAT -Wno-REDEFMACRO -Wno-CMPCONST

jtag-verilog:
	mkdir -p $(GEN_DIR)
	mill -i $(PRJ).runMain Elaborate --target-dir $(GEN_DIR)

jtag-sim: jtag-verilog
	mkdir -p $(BUILD_DIR)
	$(VERILATOR) --cc --exe --build -j 4 \
		--top-module $(TOP_MODULE) \
		-DASSERTS_OFF \
		$(VINCLUDES) \
		$(VSRCS) \
		$(CPP_SRCS) \
		--Mdir $(BUILD_DIR) \
		-o sim_top \
		$(VFLAGS)

run-jtag: jtag-sim
	$(BUILD_DIR)/sim_top

run-only-jtag:
	@echo "[Warn] Can only be used after `make jtag-sim`"
	$(BUILD_DIR)/sim_top

-include ../Makefile
