<!-- // filepath: /home/zhaocake/WorkSpace/jtag_wksps/jtag_commands.md -->
# 17. JTAG 命令

大多数通用的 JTAG 命令已在前面介绍过（参见 JTAG 速度、复位配置和 TAP 声明）。这里介绍的底层 JTAG 命令，可能在处理需要特殊关注的操作（如复位或初始化）的目标时需要用到。

要使用这些命令，你需要了解一些 JTAG 的基础知识，包括：

*   **JTAG 扫描链** 由一系列独立的 TAP 设备（如 CPU）组成。
*   **控制操作** 涉及使用共享的 TMS 和时钟信号，使每个 TAP（并行地）通过相同的标准状态机。
*   **数据传输** 涉及将数据移入每个 TAP 的指令或数据寄存器链，在写入新寄存器值的同时读出旧值。
*   **数据寄存器的大小** 是给定 TAP 中当前活动指令的函数，而每个 TAP 的指令寄存器大小是固定的。所有 TAP 都支持带有一个单比特数据寄存器的 BYPASS 指令。
*   OpenOCD 区分 TAP 设备的方式是将不同的指令移入（并移出）它们的指令寄存器。

## 17.1 底层 JTAG 命令

这些命令供开发人员使用，他们需要访问 JTAG 指令或数据寄存器，可能还需要控制 TAP 状态转换的顺序。如果你不是在调试 OpenOCD 内部，或者在启动一个新的 JTAG 适配器或一种新型的 TAP 设备（如 CPU 或 JTAG 路由器），你可能不需要使用这些命令。在不使用 JTAG 作为传输协议的调试会话中，这些命令不可用。

### `drscan`
**语法**: `drscan tap [numbits value]+ [-endstate tap_state]`

*   **描述**: 将一系列位字段加载到指定 `tap` 的数据寄存器中，这些字段共同构成了整个寄存器。每个字段由 `numbits`（位数）和 `value`（数值，建议使用十六进制）指定。返回值包含每个字段的原始值。
*   **示例**: 一个 38 位的数字可以指定为一个 32 位的字段加上一个 6 位的字段。为了可移植性，不要传递超过 32 位的字段。许多 OpenOCD 实现不支持 64 位（或更大）的整数值。
*   **注意**:
    *   除 `tap` 之外的所有其他 TAP 必须处于 BYPASS 模式。它们数据寄存器中的单个比特无关紧要。
    *   当指定 `tap_state` 时，JTAG 状态机将停留在该状态。例如，可以指定 `DRPAUSE`，以便在重新进入 `RUN/IDLE` 状态之前发出更多指令。如果未指定结束状态，则进入 `RUN/IDLE` 状态。
    *   **警告**: OpenOCD 不记录有关数据寄存器长度的信息，因此正确设置位字段长度非常重要。请记住，不同的 JTAG 指令引用不同的数据寄存器，这些寄存器可能具有不同的长度。此外，这些长度可能不是固定的；`SCAN_N` 指令可以改变 `INTEST` 指令访问的寄存器长度（通过连接不同的扫描链）。

### `flush_count`
**语法**: `flush_count`

*   **描述**: 返回 JTAG 队列被刷新的次数。这可用于性能调优。
*   **示例**: 通过 USB 刷新队列涉及最小延迟，通常为几毫秒，这不会随写入的数据量而改变。你可以通过查找那些因过于频繁地刷新小传输而浪费带宽的任务来识别性能问题，而不是将它们分批处理成更大的操作。

### `irscan`
**语法**: `irscan [tap instruction]+ [-endstate tap_state]`

*   **描述**: 对于列出的每个 `tap`，将其关联的数字指令加载到指令寄存器中。（该指令的位数可以使用 `scan_chain` 命令显示。）对于其他 TAP，加载 BYPASS 指令。
*   **注意**:
    *   当指定 `tap_state` 时，JTAG 状态机将停留在该状态。例如，可以指定 `IRPAUSE`，以便在重新进入 `RUN/IDLE` 状态之前加载数据寄存器。如果未指定结束状态，则进入 `RUN/IDLE` 状态。
    *   **注意**: OpenOCD 目前仅支持指令寄存器值的单个字段，这与数据寄存器值不同。对于指令寄存器长度超过 32 位的 TAP，可移植脚本目前必须仅发出 BYPASS 指令。

### `pathmove`
**语法**: `pathmove start_state [next_state ...]`

*   **描述**: 首先移动到 `start_state`，它必须是稳定状态之一。除非它是给出的唯一状态，否则这通常是当前状态，因此不需要 TCK 转换。然后，在一系列单状态转换（符合 JTAG 状态机）中，按顺序移动到每个 `next_state`，每个 TCK 周期一个。最终状态也必须是稳定的。

### `runtest`
**语法**: `runtest num_cycles`

*   **描述**: 移动到 `RUN/IDLE` 状态，并执行至少 `num_cycles` 个 JTAG 时钟 (TCK) 周期。指令通常需要一些时间来执行才能生效。

### `verify_ircapture`
**语法**: `verify_ircapture (enable|disable)`

*   **描述**: 验证在 `IRCAPTURE` 期间捕获并在 IR 扫描期间返回的值。默认为启用，但这可以被 `verify_jtag` 覆盖。在验证 JTAG 链配置时忽略此标志。

### `verify_jtag`
**语法**: `verify_jtag (enable|disable)`

*   **描述**: 启用 DR 和 IR 扫描的验证，以帮助检测编程错误。对于 IR 扫描，还必须启用 `verify_ircapture`。默认为启用。

## 17.2 TAP 状态名称

OpenOCD 在 `drscan`、`irscan` 和 `pathmove` 命令中使用的 `tap_state` 名称与 SVF 边界扫描文档中使用的名称相同，除了 SVF 使用 `IDLE` 代替 `RUN/IDLE`。

*   **RESET**: ... 稳定（TMS 为高）；就像 TRST 被脉冲一样
*   **RUN/IDLE**: ... 稳定；不要假设这总是意味着空闲
*   **DRSELECT**
*   **DRCAPTURE**
*   **DRSHIFT**: ... 稳定；TDI/TDO 通过数据寄存器移位
*   **DREXIT1**
*   **DRPAUSE**: ... 稳定；数据寄存器准备好更新或更多移位
*   **DREXIT2**
*   **DRUPDATE**
*   **IRSELECT**
*   **IRCAPTURE**
*   **IRSHIFT**: ... 稳定；TDI/TDO 通过指令寄存器移位
*   **IREXIT1**
*   **IRPAUSE**: ... 稳定；指令寄存器准备好更新或更多移位
*   **IREXIT2**
*   **IRUPDATE**

请注意，在 TMS 固定（除 RESET 外为低）和自由运行的 JTAG 时钟下，只有其中六个状态是完全“稳定”的。对于所有其他状态，下一个 TCK 转换将变为新状态。

*   从 `DRSHIFT` 和 `IRSHIFT` 开始，时钟转换将通过改变寄存器内容产生副作用。在即将到来的 `DRUPDATE` 或 `IRUPDATE` 状态中锁存的值可能不是预期的。
*   `RUN/IDLE`、`DRPAUSE` 和 `IRPAUSE` 是 `drscan` 或 `irscan` 命令之后的合理选择，因为它们没有 JTAG 副作用。
*   `RUN/IDLE` 可能会产生非 JTAG 级别的副作用，例如推进 ARM9E-S 指令流水线。请查阅你正在使用的 TAP 的文档。
