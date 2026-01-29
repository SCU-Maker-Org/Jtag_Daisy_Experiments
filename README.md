# JTAG 菊花链仿真项目

本项目实现了一个基于 Chisel 的双 Ibex RISC-V 核心 JTAG 菊花链设计，使用 PULP 的 riscv-dbg 作为调试模块，提供 Verilator 仿真环境和 OpenOCD 调试支持。

> 如果更注重jtag的基本工作原理，请切换到only_TAP分支进行学习

## 文档

- [调试指南](./docs/debugging_guide.md) - Telnet/GDB 调试详细说明
- [OpenOCD JTAG 底层命令](./jtag_commands.md)
- [OpenOCD RISC-V 调试架构](./docs/jtag_riscv_arch.md)

## 快速开始

```bash
# 1. 初始化子模块
git submodule update --init --recursive

# 2. 进入开发环境
nix develop

# 3. 编译测试程序
cd sw && make && cd ..

# 4. 编译并启动仿真（终端1）
make jtag-sim
./build/sim_top

# 5. 启动 OpenOCD（终端2）
openocd -f openocd.cfg

# 6. 连接调试（终端3）
telnet localhost 4444
```

## 架构

```
┌─────────────────────────────────────────────────────────┐
│                      TopMain                            │
│  ┌──────────────┐              ┌──────────────┐        │
│  │  IbexSystem  │◄── JTAG ───►│  IbexSystem  │        │
│  │  (core1)     │   Daisy     │  (core2)     │        │
│  │              │   Chain     │              │        │
│  │ ┌────────┐   │              │ ┌────────┐   │        │
│  │ │  Ibex  │   │              │ │  Ibex  │   │        │
│  │ └────────┘   │              │ └────────┘   │        │
│  │ ┌────────┐   │              │ ┌────────┐   │        │
│  │ │ dm_top │   │              │ │ dm_top │   │        │
│  │ └────────┘   │              │ └────────┘   │        │
│  │ ┌────────┐   │              │ ┌────────┐   │        │
│  │ │  RAM   │   │              │ │  RAM   │   │        │
│  │ └────────┘   │              │ └────────┘   │        │
│  └──────────────┘              └──────────────┘        │
└─────────────────────────────────────────────────────────┘
          ▲
          │ remote_bitbang (port 9823)
          ▼
      OpenOCD ──► Telnet (port 4444)
                  GDB (port 3333/3334)
```

## 内存映射

| 地址范围 | 描述 |
|---------|------|
| 0x00100000 - 0x0010FFFF | RAM (64KB) |
| 0x1A110000 - 0x1A110FFF | Debug Module |

## 依赖

- Nix (推荐) 或手动安装: Mill, Verilator, OpenOCD, RISC-V 工具链

## 已知问题

- GDB 连接有超时警告，建议使用 Telnet 调试（功能完整）
- 详见 [调试指南](./docs/debugging_guide.md#已知问题)
