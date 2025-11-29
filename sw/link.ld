/* Linker script for Ibex test programs */
/* Memory starts at 0x00100000, 64KB size */

MEMORY
{
    RAM (rwx) : ORIGIN = 0x00100000, LENGTH = 64K
}

SECTIONS
{
    .text : {
        *(.text)
        *(.text.*)
    } > RAM

    .rodata : {
        *(.rodata)
        *(.rodata.*)
    } > RAM

    .data : {
        *(.data)
        *(.data.*)
    } > RAM

    .bss : {
        *(.bss)
        *(.bss.*)
    } > RAM

    /* Stack at top of RAM */
    _stack_top = ORIGIN(RAM) + LENGTH(RAM);
}

ENTRY(_start)
