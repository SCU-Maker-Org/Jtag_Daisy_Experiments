# 16.10 RISC-V 架构

RISC-V 是一个免费和开放的指令集架构（ISA）。OpenOCD 支持对异构多核系统中多达 32 个 hart 的 RV32 和 RV64 核心进行 JTAG 调试。（通过修改 `riscv.h` 中的 `RISCV_MAX_HARTS`，可以将此限制增加到 1024。）OpenOCD 主要支持 RISC-V 调试规范的 0.13 版本，但也支持实现 0.11 版本的旧版目标。

## 16.10.1 RISC-V 术语

一个 **hart** 是一个硬件线程。一个 hart 可能与其他 hart 共享资源（例如 FPU），也可能是一个独立的核心。RISC-V 对这两种情况同等对待，OpenOCD 将每个 hart 暴露为一个独立的核心。

## 16.10.2 向量寄存器

对于实现了向量扩展的 hart，OpenOCD 提供对相关 CSR（控制与状态寄存器）以及向量寄存器（v0-v31）的访问。每个向量寄存器的大小取决于 `vlenb` 的值。RISC-V 允许将每个向量寄存器划分为选定宽度的元素，并且这种划分可以在运行时更改。由于 OpenOCD 无法在运行时更新寄存器定义，它将每个向量寄存器作为向量字段的联合体暴露给 gdb，以便用户可以轻松访问每个向量寄存器内的单个字节、半字、字、双字和四字。将数据以更直观的格式呈现，则留给 gdb 或更高级别的调试器来完成。

在 XML 寄存器描述中，向量寄存器（当 `vlenb=16` 时）如下所示：

```xml
<feature name="org.gnu.gdb.riscv.vector">
    <vector id="bytes" type="uint8" count="16"/>
    <vector id="shorts" type="uint16" count="8"/>
    <vector id="words" type="uint32" count="4"/>
    <vector id="longs" type="uint64" count="2"/>
    <vector id="quads" type="uint128" count="1"/>
    <union id="riscv_vector">
        <field name="b" type="bytes"/>
        <field name="s" type="shorts"/>
        <field name="w" type="words"/>
        <field name="l" type="longs"/>
        <field name="q" type="quads"/>
    </union>
    <reg name="v0" bitsize="128" regnum="4162" save-restore="no"
            type="riscv_vector" group="vector"/>
    ...
    <reg name="v31" bitsize="128" regnum="4193" save-restore="no"
            type="riscv_vector" group="vector"/>
</feature>
```

## 16.10.3 RISC-V 调试配置命令

### `riscv expose_csrs n[-m|=名称] [...]`

**配置命令**：配置除了标准 CSR 之外还要暴露哪些 CSR。要暴露的 CSR 可以指定为单独的寄存器号或寄存器范围（包含边界）。对于单独列出的 CSR，可以使用 `n=名称` 语法可选地设置一个人类可读的名称，该名称前会自动加上 `csr_` 前缀。如果未提供名称，寄存器将被命名为 `csr<n>`。

默认情况下，OpenOCD 尝试仅暴露规范中提到的 CSR，并且仅当相应的扩展似乎被实现时才暴露。如果 OpenOCD 的判断有误，或者目标实现了自定义的 CSR，可以使用此命令。

```tcl
# 在名称 "csr128" 下暴露单个 RISC-V CSR 号 128：
$_TARGETNAME configure -work-area-phys 0x80000000 -work-area-size 10000 -work-area-backup 0
$_TARGETNAME expose_csrs 128

# 在名称 "csr128" 到 "csr132" 下暴露多个 RISC-V CSR 128..132：
$_TARGETNAME expose_csrs 128-132

# 在自定义名称 "csr_myregister" 下暴露单个 RISC-V CSR 号 1996：
$_TARGETNAME expose_csrs 1996=myregister
```

### `riscv expose_custom n[-m|=名称] [...]`

**配置命令**：RISC-V 调试规范允许目标通过抽象命令暴露自定义寄存器。（请参阅该文档的第 3.5.1.1 节。）此命令配置要暴露的单独寄存器或寄存器范围（包含边界）。数字 0 表示第一个自定义寄存器，其抽象命令号为 `0xc000`。对于单独列出的寄存器，可以使用 `n=名称` 语法可选地提供一个人类可读的名称，该名称前会自动加上 `custom_` 前缀。如果未提供名称，寄存器将被命名为 `custom<n>`。

```tcl
# 在名称 "custom16" 下暴露一个 RISC-V 自定义寄存器，其编号为 0xc010 (0xc000 + 16)：
$_TARGETNAME expose_custom 16

# 在名称 "custom16" 到 "custom24" 下暴露一个范围的 RISC-V 自定义寄存器，
# 其编号为 0xc010 .. 0xc018 (0xc000+16 .. 0xc000+24)：
$_TARGETNAME expose_custom 16-24

# 在用户定义的名称 "custom_myregister" 下暴露一个 RISC-V 自定义寄存器，
# 其编号为 0xc020 (0xc000 + 32)：
$_TARGETNAME expose_custom 32=myregister
```

### `riscv info`

显示 OpenOCD 检测到的关于目标的一些信息。

### `riscv reset_delays [等待次数]`

OpenOCD 会学习在扫描之间需要多少个运行-测试/空闲周期以避免目标繁忙。此命令在"等待次数"次扫描后重置这些学习到的值。它仅对测试 OpenOCD 本身有用。

### `riscv set_command_timeout_sec [秒数]`

设置单个命令的实际超时时间（以秒为单位）。除了最慢的目标（例如模拟器）外，默认值应该对所有目标都适用。

### `riscv set_reset_timeout_sec [秒数]`

设置在复位信号撤销后，等待 hart 退出复位状态的最大时间。

### `riscv set_mem_access 方法1 [方法2] [方法3]`

指定应使用哪些 RISC-V 内存访问方法及其优先级顺序。必须至少指定一种方法。

可用方法有：

| 方法 | 描述 |
|------|------|
| `progbuf` | 使用 RISC-V 调试程序缓冲区访问内存 |
| `sysbus` | 通过 RISC-V 调试系统总线接口访问内存 |
| `abstract` | 通过 RISC-V 调试抽象命令访问内存 |

默认情况下，所有内存访问方法都按以下顺序启用：`progbuf sysbus abstract`。如果默认行为不适用于特定目标，可以使用此命令来更改内存访问方法。

### `riscv set_enable_virtual on|off`

当设置为 `on` 时，内存访问是根据当前系统配置在物理内存或虚拟内存上执行。当设置为 `off`（默认）时，所有内存访问都在物理内存上执行。

### `riscv set_enable_virt2phys on|off`

当设置为 `on`（默认）时，内存访问是根据当前的 satp 配置在物理内存或虚拟内存上执行。当设置为 `off` 时，所有内存访问都在物理内存上执行。

### `riscv resume_order normal|reversed`

某些软件假设所有 hart 都在近乎连续地执行。此类软件可能对 hart 恢复执行的顺序很敏感。对于不支持 hasel 的 hart，此选项允许用户选择 hart 恢复执行的顺序。如果您需要使用此选项，这很可能是在掩盖您代码中的竞态条件问题。

- **正常顺序**：从最低的 hart 索引到最高的 hart 索引（默认）
- **反向顺序**：从最高的 hart 索引到最低的 hart 索引

### `riscv set_ir (idcode|dtmcs|dmi) [值]`

为指定的 JTAG 寄存器设置 IR（指令寄存器）值。例如，当通过 BSCANE2 原语使用 Xilinx FPGA 上现有的 JTAG 接口时，这很有用，因为这些原语只允许有限的 IR 值选择。

当使用 RISC-V 调试规范的 0.11 版本时，`dtmcs` 和 `dmi` 分别为 DTMCONTROL 和 DBUS 寄存器设置 IR 值。

### `riscv use_bscan_tunnel 值`

启用或禁用使用 BSCAN 隧道来访问 DM（调试模块）。提供 DM 传输 TAP（测试访问端口）的指令寄存器宽度以启用该功能。提供值 `0` 以禁用。

### `riscv set_ebreakm on|off`

控制 `dcsr.ebreakm`。当设置为 `on`（默认）时，M-mode 的 ebreak 指令会陷入 OpenOCD。当设置为 `off` 时，它们会生成由目标内部处理的断点异常。

### `riscv set_ebreaks on|off`

控制 `dcsr.ebreaks`。当设置为 `on`（默认）时，S-mode 的 ebreak 指令会陷入 OpenOCD。当设置为 `off` 时，它们会生成由目标内部处理的断点异常。

### `riscv set_ebreaku on|off`

控制 `dcsr.ebreaku`。当设置为 `on`（默认）时，U-mode 的 ebreak 指令会陷入 OpenOCD。当设置为 `off` 时，它们会生成由目标内部处理的断点异常。

## 16.10.4 RISC-V 身份验证命令

以下命令可用于向 RISC-V 系统进行身份验证。例如，可以在配置文件中实现一个简单的挑战-响应协议，紧跟在 `init` 命令之后：

```tcl
set challenge [riscv authdata_read]
riscv authdata_write [expr {$challenge + 1}]
```

### `riscv authdata_read`

返回从 authdata 读取的 32 位值。

### `riscv authdata_write 值`

将 32 位的 `值` 写入 authdata。

## 16.10.5 RISC-V DMI 命令

以下命令允许直接访问调试模块接口，可用于与自定义调试功能交互。

### `riscv dmi_read 地址`

在指定的 `地址` 执行 32 位的 DMI 读取，并返回读取到的值。

### `riscv dmi_write 地址 值`

在指定的 `地址` 执行 32 位的 DMI 写入，写入的值为 `值`。
