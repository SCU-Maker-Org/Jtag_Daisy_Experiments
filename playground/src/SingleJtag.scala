package simjtag

import chisel3._
import chisel3.util._

// ==========================================
// JTAG 基础组件定义
// ==========================================

/**
 * JTAG 物理接口 IO 定义
 */
class JtagIO extends Bundle {
  val TCK = Input(Clock())
  val TMS = Input(Bool())
  val TDI = Input(Bool())
  val TDO = Output(Bool())
}

/**
 * JTAG 状态机状态枚举
 */
object JtagState extends ChiselEnum {
  val TestLogicReset, RunTestIdle = Value
  val SelectDRScan, CaptureDR, ShiftDR, Exit1DR, PauseDR, Exit2DR, UpdateDR = Value
  val SelectIRScan, CaptureIR, ShiftIR, Exit1IR, PauseIR, Exit2IR, UpdateIR = Value
}

/**
 * JTAG TAP 控制器
 * 实现标准的 16 状态 JTAG 状态机
 * 
 * @param irLength 指令寄存器长度
 * @param initialInstruction 复位时的默认指令
 */
class JtagTapController(irLength: Int, initialInstruction: Int) extends Module {
  require(irLength >= 2, "JTAG IR length must be at least 2 bits")
  require(initialInstruction < (1 << irLength), "Initial instruction must fit in IR")

  val io = IO(new Bundle {
    val jtag = new JtagIO
    val control = new Bundle {
      val instruction = Output(UInt(irLength.W))
      val state = Output(JtagState())
    }
    val chainIn = Input(Bool())   // 来自数据寄存器链的 TDO (Shift-DR 时移出)
    val chainOut = Output(Bool()) // 输出到数据寄存器链的 TDI (Shift-DR 时移入)
  })

  import JtagState._

  // 状态寄存器，使用 TCK 作为时钟
  // 注意：在实际 FPGA 中，TCK 通常作为时钟树的一部分。
  // 这里使用 withClock 显式指定时钟域。
  val state = withClock(io.jtag.TCK) { RegInit(TestLogicReset) }
  
  // 状态转移逻辑
  withClock(io.jtag.TCK) {
    val nextState = WireDefault(state)
    switch(state) {
      is(TestLogicReset) { nextState := Mux(io.jtag.TMS, TestLogicReset, RunTestIdle) }
      is(RunTestIdle)    { nextState := Mux(io.jtag.TMS, SelectDRScan, RunTestIdle) }
      
      // DR 路径
      is(SelectDRScan)   { nextState := Mux(io.jtag.TMS, SelectIRScan, CaptureDR) }
      is(CaptureDR)      { nextState := Mux(io.jtag.TMS, Exit1DR, ShiftDR) }
      is(ShiftDR)        { nextState := Mux(io.jtag.TMS, Exit1DR, ShiftDR) }
      is(Exit1DR)        { nextState := Mux(io.jtag.TMS, UpdateDR, PauseDR) }
      is(PauseDR)        { nextState := Mux(io.jtag.TMS, Exit2DR, PauseDR) }
      is(Exit2DR)        { nextState := Mux(io.jtag.TMS, UpdateDR, ShiftDR) }
      is(UpdateDR)       { nextState := Mux(io.jtag.TMS, SelectDRScan, RunTestIdle) }
      
      // IR 路径
      is(SelectIRScan)   { nextState := Mux(io.jtag.TMS, TestLogicReset, CaptureIR) }
      is(CaptureIR)      { nextState := Mux(io.jtag.TMS, Exit1IR, ShiftIR) }
      is(ShiftIR)        { nextState := Mux(io.jtag.TMS, Exit1IR, ShiftIR) }
      is(Exit1IR)        { nextState := Mux(io.jtag.TMS, UpdateIR, PauseIR) }
      is(PauseIR)        { nextState := Mux(io.jtag.TMS, Exit2IR, PauseIR) }
      is(Exit2IR)        { nextState := Mux(io.jtag.TMS, UpdateIR, ShiftIR) }
      is(UpdateIR)       { nextState := Mux(io.jtag.TMS, SelectDRScan, RunTestIdle) }
    }
    state := nextState
  }

  // 指令寄存器逻辑
  val instructionReg = withClock(io.jtag.TCK) { RegInit(initialInstruction.U(irLength.W)) }
  val instructionShiftReg = withClock(io.jtag.TCK) { Reg(UInt(irLength.W)) }

  withClock(io.jtag.TCK) {
    // 当进入 TestLogicReset 时，复位指令寄存器
    when(state === TestLogicReset) {
      instructionReg := initialInstruction.U
    } .elsewhen(state === UpdateIR) {
      instructionReg := instructionShiftReg
    }

    // Shift-IR 路径
    when(state === CaptureIR) {
      // JTAG 规范要求 Capture-IR 阶段加载低两位为 01 的模式
      // 明确地将最低两位设为 01，其余高位为 0
      instructionShiftReg := Cat(0.U((irLength - 2).W), "b01".U(2.W))
    } .elsewhen(state === ShiftIR) {
      // TDI 应该移入 MSB，数据向右移，LSB 移出。
      instructionShiftReg := Cat(io.jtag.TDI, instructionShiftReg(irLength-1, 1))
    }
  }

  io.control.instruction := instructionReg
  io.control.state := state
  io.chainOut := io.jtag.TDI // 数据直接透传给数据链

  // TDO 输出逻辑
  // Shift-IR 时输出指令移位寄存器的 LSB (instructionShiftReg(0))
  // Shift-DR 时输出数据链的返回数据 (io.chainIn)
  val tdoMux = Mux(state === ShiftIR, instructionShiftReg(0), io.chainIn)
  
  // TDO 使能：只有在 Shift 状态才驱动
  val tdoEn = state === ShiftIR || state === ShiftDR
  
  // TDO 必须在 TCK 下降沿改变
  // 使用 TCK 的反相作为时钟来驱动 TDO 输出寄存器
  val tdoReg = withClock((!io.jtag.TCK.asBool).asClock) { RegNext(tdoMux, false.B) }
  
  // 当不在 Shift 状态时，TDO 应该为高阻态 (在 FPGA 内部逻辑通常置 0 或 1，这里置 0)
  // 如果是顶层 IO，可能需要 Tri-state buffer。这里简化为输出。
  io.jtag.TDO := tdoReg // 实际应用中可能需要配合 tdoEn 控制三态门
}

/**
 * 通用的 Capture-Update 链
 * 用于实现数据寄存器 (DR)
 */
class CaptureUpdateChain(width: Int) extends Module {
  val io = IO(new Bundle {
    val tck = Input(Clock())
    val chainIn = Input(Bool())
    val chainOut = Output(Bool())
    
    // 控制信号
    val capture = Input(Bool())
    val shift = Input(Bool())
    val update = Input(Bool())
    
    // 并行数据接口
    val parallelIn = Input(UInt(width.W))  // Capture 阶段载入的数据
    val parallelOut = Output(UInt(width.W)) // Update 阶段输出的数据
  })
  
  withClock(io.tck) {
    val shiftReg = Reg(UInt(width.W))
    
    // Shift / Capture 逻辑
    when(io.capture) {
      shiftReg := io.parallelIn
    } .elsewhen(io.shift) {
      // 向右移位: TDI(chainIn) -> MSB ... LSB -> TDO(chainOut)
      if (width == 1) {
        // 对于 1-bit 寄存器 (如 Bypass)，直接移入 TDI
        shiftReg := io.chainIn
      } else {
        // 对于多位寄存器，TDI 补到最高位，低位右移
        shiftReg := Cat(io.chainIn, shiftReg(width-1, 1))
      }
    }
    
    io.chainOut := shiftReg(0)
    
    // Update 逻辑
    val updateReg = RegInit(0.U(width.W))
    when(io.update) {
      updateReg := shiftReg
    }
    io.parallelOut := updateReg
  }
}

// ==========================================
// Top Module: SingleJtag
// ==========================================

/* 
实现单个JTAG接口的Top模块
包含:
1. JTAG IO
2. JTAG TAP Controller
3. IDCODE 寄存器
4. 一个简单的 8-bit 数据寄存器 (用于读写测试)
 */
class SingleJtag(idcode: Int = 0x1) extends Module {
  require(idcode % 2 == 1, "IDCODE LSB must be 1 according to JTAG standard")

  val io = IO(new Bundle {
    val jtag = new JtagIO
    // 用于观察内部状态的输出，例如连接到LED
    val led = Output(UInt(8.W)) 
  })

  // 1. 实例化 JTAG TAP 控制器
  // irLength = 4: 指令寄存器长度为4位
  // initialInstruction = 1: 默认指令设为 IDCODE (1)
  val tap = Module(new JtagTapController(irLength = 4, initialInstruction = 1))
  
  tap.io.jtag <> io.jtag

  // 2. 定义指令 (Instruction)
  val IDCODE_INSTR = "b0001".U(4.W)    // IDCODE 指令
  val USER_DATA_INSTR = "b0010".U(4.W) // 用户自定义数据指令
  val BYPASS_INSTR = "b1111".U(4.W)    // BYPASS 指令

  // 获取当前指令和状态
  val curInstr = tap.io.control.instruction
  val curState = tap.io.control.state

  // 3. IDCODE 链逻辑
  val idcodeChain = Module(new CaptureUpdateChain(32))
  idcodeChain.io.tck := io.jtag.TCK
  idcodeChain.io.chainIn := tap.io.chainOut
  idcodeChain.io.parallelIn := idcode.U(32.W) // IDCODE 是固定的
  
  // 控制信号生成
  idcodeChain.io.capture := (curState === JtagState.CaptureDR) && (curInstr === IDCODE_INSTR)
  idcodeChain.io.shift   := (curState === JtagState.ShiftDR)   && (curInstr === IDCODE_INSTR)
  idcodeChain.io.update  := (curState === JtagState.UpdateDR)  && (curInstr === IDCODE_INSTR)

  // 4. 用户数据链逻辑 (简单的 8-bit 寄存器)
  val userChain = Module(new CaptureUpdateChain(8))
  userChain.io.tck := io.jtag.TCK
  userChain.io.chainIn := tap.io.chainOut
  // Capture 阶段回读当前的 LED 状态 (Loopback)
  userChain.io.parallelIn := userChain.io.parallelOut 
  
  userChain.io.capture := (curState === JtagState.CaptureDR) && (curInstr === USER_DATA_INSTR)
  userChain.io.shift   := (curState === JtagState.ShiftDR)   && (curInstr === USER_DATA_INSTR)
  userChain.io.update  := (curState === JtagState.UpdateDR)  && (curInstr === USER_DATA_INSTR)

  // 将 Update 后的数据输出到 LED
  io.led := userChain.io.parallelOut

  // 5. Bypass 链逻辑 (1-bit)
  val bypassChain = Module(new CaptureUpdateChain(1))
  bypassChain.io.tck := io.jtag.TCK
  bypassChain.io.chainIn := tap.io.chainOut
  bypassChain.io.parallelIn := 0.U // Bypass capture 0
  
  // 智能判断 Bypass 模式：
  // 1. 显式的 BYPASS 指令
  // 2. 任何未定义的指令 (JTAG 标准强制要求未定义指令走 Bypass)
  val definedInstructions = Seq(IDCODE_INSTR, USER_DATA_INSTR)
  val isInstructionDefined = definedInstructions.map(curInstr === _).reduce(_ || _)
  val isBypass = !isInstructionDefined || (curInstr === BYPASS_INSTR)

  bypassChain.io.capture := (curState === JtagState.CaptureDR) && isBypass
  bypassChain.io.shift   := (curState === JtagState.ShiftDR)   && isBypass
  bypassChain.io.update  := (curState === JtagState.UpdateDR)  && isBypass

  // 6. 数据链输出多路选择 (TDO Mux)
  // 根据当前指令选择哪个链的输出回到 TAP 控制器
  // MuxLookup 的默认值 (default) 设为 Bypass 链的输出，以处理未定义指令
  tap.io.chainIn := MuxLookup(curInstr, bypassChain.io.chainOut)(Seq(
    IDCODE_INSTR -> idcodeChain.io.chainOut,
    USER_DATA_INSTR -> userChain.io.chainOut
  ))
}

