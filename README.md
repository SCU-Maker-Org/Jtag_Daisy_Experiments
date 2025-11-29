# JTAG 菊花链仿真项目

[Jtag底层OpenOCD命令](./jtag_commands.md)
[Core集成进度](./with_core.md)

本项目实现了一个基于 Chisel 的双 TAP JTAG 菊花链设计，并提供了 Verilator 仿真环境和 OpenOCD 配置文件，用于验证 JTAG 协议栈和菊花链拓扑。

## 依赖工具

*   **Mill**: Scala 构建工具
*   **Verilator**: 开源 Verilog 仿真器
*   **OpenOCD**: 调试软件 (需支持 `remote_bitbang` 驱动)
*   **GCC/G++**: C++ 编译器

## 运行步骤

### 1. 启动仿真器

在一个终端窗口中运行以下命令。这将编译 Chisel 代码，生成 Verilator 模型，并启动仿真服务器（监听端口 9823）。

```bash
make run-jtag
```

等待终端显示：
```
Waiting for OpenOCD connection on port 9823...
```

### 2. 启动 OpenOCD

先编译sw中的软件。

```bash
make -C sw
```

打开另一个终端窗口，运行 OpenOCD 连接到仿真器：

```bash
openocd -f openocd.cfg
```

### 3. 观察结果

*   **OpenOCD 终端**: 将显示扫描到的 TAP 设备 (`Tap1 found`, `Tap2 found`) 以及测试脚本的执行结果。
*   **仿真器终端**: 将显示连接状态和仿真运行情况。

## 项目结构

*   `playground/src/SingleJtag.scala`: 单个 JTAG TAP 控制器实现 (含 IDCODE 和用户数据寄存器)。
*   `playground/src/TopMain.scala`: 顶层模块，实例化两个 TAP 并连接成菊花链。
*   `csrc/sim_main.cpp`: Verilator 仿真主程序，包含 TCP 服务器以适配 OpenOCD remote_bitbang 协议。
*   `openocd.cfg`: OpenOCD 配置文件，定义了菊花链拓扑和测试脚本。
