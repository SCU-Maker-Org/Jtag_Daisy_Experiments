package src

import chisel3._
import chisel3.util._

class IbexSystem(idcode: Int) extends Module {
  val io = IO(new Bundle {
    val jtag_tck = Input(Clock())
    val jtag_tms = Input(Bool())
    val jtag_trst_n = Input(Bool())
    val jtag_tdi = Input(Bool())
    val jtag_tdo = Output(Bool())
    val jtag_tdo_oe = Output(Bool())
  })

  val ibex = Module(new IbexTop)
  val dm = Module(new DmTop)
  val dmi = Module(new DmiJtag(idcode))

  // --- DMI Connection ---
  dmi.io.clk_i := clock
  dmi.io.rst_ni := !reset.asBool
  dmi.io.testmode_i := false.B
  dmi.io.tck_i := io.jtag_tck
  dmi.io.tms_i := io.jtag_tms
  dmi.io.trst_ni := io.jtag_trst_n
  dmi.io.td_i := io.jtag_tdi
  io.jtag_tdo := dmi.io.td_o
  io.jtag_tdo_oe := dmi.io.tdo_oe_o

  dmi.io.dmi_req_ready_i := dm.io.dmi_req_ready_o
  dmi.io.dmi_resp_valid_i := dm.io.dmi_resp_valid_o
  dmi.io.dmi_resp_i := dm.io.dmi_resp_o

  dm.io.dmi_rst_ni := dmi.io.dmi_rst_no
  dm.io.dmi_req_valid_i := dmi.io.dmi_req_valid_o
  dm.io.dmi_req_i := dmi.io.dmi_req_o
  dm.io.dmi_resp_ready_i := dmi.io.dmi_resp_ready_o

  // --- DM Configuration ---
  dm.io.clk_i := clock
  dm.io.rst_ni := !reset.asBool
  dm.io.next_dm_addr_i := 0.U
  dm.io.testmode_i := false.B
  // ndmreset_ack_i should only pulse when ndmreset completes, not be held high
  // Use a register to track when we've acknowledged the reset
  val ndmreset_prev = RegNext(dm.io.ndmreset_o, false.B)
  val ndmreset_falling_edge = ndmreset_prev && !dm.io.ndmreset_o
  dm.io.ndmreset_ack_i := ndmreset_falling_edge
  dm.io.unavailable_i := 0.U
  dm.io.hartinfo_i := 0x00212380.U 

  // --- Ibex Configuration ---
  ibex.io.clk_i := clock
  ibex.io.rst_ni := !reset.asBool & !dm.io.ndmreset_o
  ibex.io.test_en_i := false.B
  ibex.io.ram_cfg_i := 0.U
  ibex.io.hart_id_i := 0.U
  ibex.io.boot_addr_i := 0x00100000.U
  ibex.io.irq_software_i := false.B
  ibex.io.irq_timer_i := false.B
  ibex.io.irq_external_i := false.B
  ibex.io.irq_fast_i := 0.U
  ibex.io.irq_nm_i := false.B
  ibex.io.scramble_key_valid_i := false.B
  ibex.io.scramble_key_i := 0.U
  ibex.io.scramble_nonce_i := 0.U
  ibex.io.debug_req_i := dm.io.debug_req_o(0)
  // Set fetch_enable_i to IbexMuBiOn (0x5 = 4'b0101) to allow the core to run
  // The core needs to be able to execute instructions to respond to debug requests
  // IbexMuBiOn = 4'b0101 (0x5), IbexMuBiOff = 4'b1010 (0xA)
  ibex.io.fetch_enable_i := 0x5.U 
  ibex.io.scan_rst_ni := true.B

  // --- Memory ---
  val mem = SyncReadMem(16384, Vec(4, UInt(8.W))) // 64KB

  // --- Address Decoding ---
  def isDmAddr(addr: UInt) = (addr >= 0x1A110000.U) && (addr <= 0x1A110FFF.U)
  def isRamAddr(addr: UInt) = (addr >= 0x00100000.U) && (addr < 0x00110000.U)

  // --- DM Slave Arbiter (Debug ROM & Data Window) ---
  // Inputs: Ibex Instr (Read), Ibex Data (Read/Write)
  val instr_req_dm = ibex.io.instr_req_o && isDmAddr(ibex.io.instr_addr_o)
  val core_req_dm = ibex.io.data_req_o && isDmAddr(ibex.io.data_addr_o)

  // Simple priority: Core Data > Instr
  val dm_slave_sel_core = core_req_dm
  val dm_slave_sel_instr = instr_req_dm && !core_req_dm

  dm.io.slave_req_i := dm_slave_sel_core || dm_slave_sel_instr
  dm.io.slave_we_i := Mux(dm_slave_sel_core, ibex.io.data_we_o, false.B)
  dm.io.slave_addr_i := Mux(dm_slave_sel_core, ibex.io.data_addr_o, ibex.io.instr_addr_o)
  dm.io.slave_wdata_i := ibex.io.data_wdata_o
  dm.io.slave_be_i := Mux(dm_slave_sel_core, ibex.io.data_be_o, 0xF.U)

  // DM Slave Response
  // Assuming 1 cycle latency for DM Slave
  val dm_slave_rvalid = RegNext(dm.io.slave_req_i)
  val dm_slave_rdata = dm.io.slave_rdata_o
  
  // Route response back
  val dm_slave_resp_to_core = RegNext(dm_slave_sel_core)
  val dm_slave_resp_to_instr = RegNext(dm_slave_sel_instr)

  // --- RAM Arbiter ---
  // Inputs: Ibex Instr, Ibex Data, DM SBA
  // Priority: DM SBA > Ibex Data > Ibex Instr
  
  val sba_req = dm.io.master_req_o
  val sba_addr_in_ram = isRamAddr(dm.io.master_add_o)
  val core_req_ram = ibex.io.data_req_o && isRamAddr(ibex.io.data_addr_o)
  val instr_req_ram = ibex.io.instr_req_o && isRamAddr(ibex.io.instr_addr_o)

  val ram_sel_sba = sba_req && sba_addr_in_ram
  val ram_sel_core = core_req_ram && !ram_sel_sba
  val ram_sel_instr = instr_req_ram && !core_req_ram && !ram_sel_sba

  val ram_req = ram_sel_sba || ram_sel_core || ram_sel_instr
  val ram_we = Mux(ram_sel_sba, dm.io.master_we_o, Mux(ram_sel_core, ibex.io.data_we_o, false.B))
  val ram_addr = Mux(ram_sel_sba, dm.io.master_add_o, Mux(ram_sel_core, ibex.io.data_addr_o, ibex.io.instr_addr_o))
  val ram_wdata = Mux(ram_sel_sba, dm.io.master_wdata_o, ibex.io.data_wdata_o)
  val ram_be = Mux(ram_sel_sba, dm.io.master_be_o, Mux(ram_sel_core, ibex.io.data_be_o, 0xF.U))

  when(ram_req && ram_we) {
    val mask = Wire(Vec(4, Bool()))
    (0 until 4).foreach(i => mask(i) := ram_be(i))
    val wdataVec = Wire(Vec(4, UInt(8.W)))
    wdataVec(0) := ram_wdata(7, 0)
    wdataVec(1) := ram_wdata(15, 8)
    wdataVec(2) := ram_wdata(23, 16)
    wdataVec(3) := ram_wdata(31, 24)
    mem.write(ram_addr(15, 2), wdataVec, mask)
  }
  val ram_rdata_vec = mem.read(ram_addr(15, 2), ram_req && !ram_we)
  val ram_rdata = Cat(ram_rdata_vec(3), ram_rdata_vec(2), ram_rdata_vec(1), ram_rdata_vec(0))
  
  // Track read vs write for proper response timing
  val ram_was_read = RegNext(ram_req && !ram_we)
  val ram_was_write = RegNext(ram_req && ram_we)

  // Route RAM response
  val ram_resp_to_sba = RegNext(ram_sel_sba)
  val ram_resp_to_core = RegNext(ram_sel_core)
  val ram_resp_to_instr = RegNext(ram_sel_instr)

  // --- Default Slave (Error Slave) ---
  // Catch-all for unmapped addresses to prevent core lockup
  val unmapped_instr = !isDmAddr(ibex.io.instr_addr_o) && !isRamAddr(ibex.io.instr_addr_o)
  val unmapped_data = !isDmAddr(ibex.io.data_addr_o) && !isRamAddr(ibex.io.data_addr_o)

  val err_slave_gnt_instr = unmapped_instr && ibex.io.instr_req_o
  val err_slave_gnt_data = unmapped_data && ibex.io.data_req_o
  
  val err_slave_rvalid_instr = RegNext(err_slave_gnt_instr)
  val err_slave_rvalid_data = RegNext(err_slave_gnt_data)

  // --- Ibex Instr Port ---
  // Grant must be given in the same cycle as the request for Ibex
  ibex.io.instr_gnt_i := dm_slave_sel_instr || ram_sel_instr || err_slave_gnt_instr
  ibex.io.instr_rvalid_i := (dm_slave_resp_to_instr && dm_slave_rvalid) || (ram_resp_to_instr && ram_was_read) || err_slave_rvalid_instr
  ibex.io.instr_rdata_i := Mux(dm_slave_resp_to_instr, dm_slave_rdata, ram_rdata)
  ibex.io.instr_rdata_intg_i := 0.U
  ibex.io.instr_err_i := err_slave_rvalid_instr

  // --- Ibex Data Port ---
  ibex.io.data_gnt_i := dm_slave_sel_core || ram_sel_core || err_slave_gnt_data
  ibex.io.data_rvalid_i := (dm_slave_resp_to_core && dm_slave_rvalid) || (ram_resp_to_core && (ram_was_read || ram_was_write)) || err_slave_rvalid_data
  ibex.io.data_rdata_i := Mux(dm_slave_resp_to_core, dm_slave_rdata, ram_rdata)
  ibex.io.data_rdata_intg_i := 0.U
  ibex.io.data_err_i := err_slave_rvalid_data

  // --- DM SBA Port ---
  // SBA should always get grant when it requests (it has highest priority for RAM)
  // But SBA can also access addresses outside RAM - need to handle that
  val sba_addr_valid = sba_addr_in_ram  // For now, only RAM is valid for SBA
  dm.io.master_gnt_i := sba_req && sba_addr_valid
  dm.io.master_r_valid_i := ram_resp_to_sba && (ram_was_read || ram_was_write)
  dm.io.master_r_rdata_i := ram_rdata
  // Return error if SBA accesses invalid address
  dm.io.master_r_err_i := RegNext(sba_req && !sba_addr_valid)
  dm.io.master_r_other_err_i := false.B

}
