# 双 Ibex 核心 JTAG 调试指南

## 快速开始

### 1. 启动仿真
```bash
# 编译并运行仿真
make jtag-sim
./build/sim_top
```

### 2. 连接 OpenOCD（在另一个终端）
```bash
openocd -f openocd.cfg
```

### 3. 通过 Telnet 连接（在第三个终端）
```bash
telnet localhost 4444
```

## 常用 OpenOCD 命令

### 目标管理
```tcl
# 列出所有目标
targets

# 选择特定目标
targets core1
targets core2

# 显示目标状态
targets
```

### 执行控制
```tcl
# 暂停当前目标
halt

# 恢复执行
resume

# 单步执行
step

# 复位并暂停
reset halt
```

### 寄存器访问
```tcl
# 显示所有寄存器
reg

# 读取特定寄存器
reg pc
reg sp
reg x10

# 写入寄存器
reg pc 0x00100000
reg x10 0x12345678
```

### 内存访问
```tcl
# 读取内存（按字显示）
mdw 0x00100000 4        # 从 0x00100000 开始读取 4 个字
mdb 0x00100000 16       # 读取 16 个字节
mdh 0x00100000 8        # 读取 8 个半字

# 写入内存
mww 0x00100000 0x0000006f   # 写入字
mwb 0x00100100 0xFF         # 写入字节
mwh 0x00100200 0x1234       # 写入半字
```

### 加载程序
```tcl
# 加载二进制文件
load_image sw/test.bin 0x00100000 bin

# 加载 ELF 文件
load_image sw/test.elf

# 验证已加载的镜像
verify_image sw/test.bin 0x00100000
```

## 内存映射

| 地址范围                 | 大小  | 描述               |
|-------------------------|-------|-------------------|
| 0x00100000 - 0x0010FFFF | 64KB  | 主内存 (RAM)       |
| 0x1A110000 - 0x1A110FFF | 4KB   | 调试模块 (DM)      |

## 调试示例

```tcl
# 1. 暂停核心
halt

# 2. 查看当前 PC
reg pc

# 3. 手动写入简单测试程序
#    li t0, 0x12345678  ->  lui t0, 0x12345; addi t0, t0, 0x678
mww 0x00100000 0x123452b7    # lui t0, 0x12345
mww 0x00100004 0x67828293    # addi t0, t0, 0x678

# 4. 添加无限循环
mww 0x00100008 0x0000006f    # j .

# 5. 设置 PC 到起始位置
reg pc 0x00100000

# 6. 单步执行
step
reg t0    # 应该是 0x12345000
step  
reg t0    # 应该是 0x12345678
step
reg pc    # 应该仍在 0x00100008（无限循环）

# 7. 恢复执行
resume

# 8. 再次暂停并检查
halt
reg pc
```

## GDB 连接

### 基本连接

```bash
# 进入 nix 开发环境
nix develop

# 启动 GDB 并加载 ELF 文件（推荐）
gdb sw/test.elf

# 在 GDB 中连接到 OpenOCD
(gdb) set arch riscv:rv32
(gdb) target remote localhost:3333    # 连接 core2
# 或
(gdb) target remote localhost:3334    # 连接 core1
```

### 完整调试流程

```bash
# 1. 启动 GDB 并加载程序
gdb sw/test.elf

# 2. 连接到目标
(gdb) target remote localhost:3333

# 3. 加载程序到目标内存
(gdb) load

# 4. 设置断点
(gdb) break _start
(gdb) break loop

# 5. 运行程序
(gdb) continue

# 6. 单步调试
(gdb) step       # 单步（进入函数）
(gdb) next       # 单步（跳过函数）
(gdb) stepi      # 单条指令

# 7. 查看寄存器
(gdb) info registers
(gdb) info reg pc
(gdb) info reg t0 t1 t2

# 8. 查看内存
(gdb) x/4xw 0x00100000    # 显示 4 个字
(gdb) x/10i $pc           # 反汇编当前位置的 10 条指令

# 9. 修改寄存器/内存
(gdb) set $pc = 0x00100000
(gdb) set {int}0x00100100 = 0x12345678
```

### 无符号文件时的连接

如果没有 ELF 文件，可以直接连接：

```bash
gdb
(gdb) set arch riscv:rv32
(gdb) target remote localhost:3333
```

> **注意**：如果看到 `Ignoring packet error, continuing...` 警告，通常是因为：
> 1. 仿真速度较慢，增加 OpenOCD 超时时间
> 2. 核心处于复位状态，先在 OpenOCD telnet 中执行 `halt`

### GDB 常用命令速查

| 命令 | 说明 |
|------|------|
| `target remote HOST:PORT` | 连接远程目标 |
| `load` | 加载程序到目标 |
| `continue` / `c` | 继续执行 |
| `step` / `s` | 单步（进入函数） |
| `next` / `n` | 单步（跳过函数） |
| `stepi` / `si` | 单条指令 |
| `break ADDR` | 设置断点 |
| `delete` | 删除所有断点 |
| `info registers` | 显示所有寄存器 |
| `x/FMT ADDR` | 查看内存 |
| `disassemble` | 反汇编当前函数 |
| `monitor CMD` | 发送命令到 OpenOCD |

### 通过 GDB 控制 OpenOCD

```gdb
# 在 GDB 中执行 OpenOCD 命令
(gdb) monitor halt
(gdb) monitor resume
(gdb) monitor reg pc
(gdb) monitor mdw 0x00100000 4
```

## 故障排除

### 内存访问超时
仿真可能比真实硬件慢。尝试增加超时时间：
```tcl
riscv set_command_timeout_sec 30
```

### SBA（系统总线访问）失败
如果抽象命令失败，OpenOCD 会尝试使用程序缓冲区或 SBA。
确保内存地址在有效范围内。
