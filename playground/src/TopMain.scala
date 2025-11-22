package simjtag

import chisel3._

/**
 * JTAG 菊花链顶层模块
 * 包含两个串联的 SingleJtag 模块
 */
class TopMain extends Module {
  val io = IO(new Bundle {
    val jtag = new JtagIO
    // 暴露两个 TAP 的 LED 输出以便观察
    val led1 = Output(UInt(8.W))
    val led2 = Output(UInt(8.W))
  })

  // 实例化两个 JTAG TAP
  // 注意：IDCODE 必须是奇数
  val tap1 = Module(new SingleJtag(idcode = 0x10000001))
  val tap2 = Module(new SingleJtag(idcode = 0x20000001))

  // 连接 JTAG 信号 - 菊花链拓扑
  // TCK, TMS 并联
  tap1.io.jtag.TCK := io.jtag.TCK
  tap1.io.jtag.TMS := io.jtag.TMS
  
  tap2.io.jtag.TCK := io.jtag.TCK
  tap2.io.jtag.TMS := io.jtag.TMS

  // 数据链路串联: TDI -> TAP1 -> TAP2 -> TDO
  tap1.io.jtag.TDI := io.jtag.TDI
  tap2.io.jtag.TDI := tap1.io.jtag.TDO
  io.jtag.TDO      := tap2.io.jtag.TDO

  // 连接 LED 输出
  io.led1 := tap1.io.led
  io.led2 := tap2.io.led
}

