#include <verilated.h>
#include "VTopMain.h"
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>

// OpenOCD remote_bitbang 协议端口
#define PORT 9823

VTopMain* top;
vluint64_t main_time = 0;

double sc_time_stamp() {
    return main_time;
}

int server_fd, new_socket;

void setup_socket() {
    struct sockaddr_in address;
    int opt = 1;
    int addrlen = sizeof(address);

    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        perror("socket failed");
        exit(EXIT_FAILURE);
    }

    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR | SO_REUSEPORT, &opt, sizeof(opt))) {
        perror("setsockopt");
        exit(EXIT_FAILURE);
    }

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(PORT);

    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        perror("bind failed");
        exit(EXIT_FAILURE);
    }

    if (listen(server_fd, 3) < 0) {
        perror("listen");
        exit(EXIT_FAILURE);
    }

    printf("Waiting for OpenOCD connection on port %d...\n", PORT);
    if ((new_socket = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen)) < 0) {
        perror("accept");
        exit(EXIT_FAILURE);
    }
    printf("OpenOCD connected!\n");
}

void tick() {
    top->clock = 0;
    top->eval();
    main_time++;
    top->clock = 1;
    top->eval();
    main_time++;
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    top = new VTopMain;

    // 复位序列
    top->reset = 1;
    top->io_jtag_TCK = 0;
    top->io_jtag_TMS = 1;
    top->io_jtag_TDI = 0;
    for (int i = 0; i < 10; i++) tick();
    top->reset = 0;
    
    setup_socket();

    // 记录 LED 状态以便检测变化
    uint8_t prev_led1 = top->io_led1;
    uint8_t prev_led2 = top->io_led2;
    printf("[Sim] Initial LED State -> LED1: 0x%02x, LED2: 0x%02x\n", prev_led1, prev_led2);

    char buffer[1];
    while (!Verilated::gotFinish()) {
        ssize_t valread = read(new_socket, buffer, 1);
        if (valread <= 0) {
            // 连接关闭或错误
            printf("Connection closed, waiting for new connection...\n");
            close(new_socket);
            // 重新接受连接
            struct sockaddr_in address;
            int addrlen = sizeof(address);
            if ((new_socket = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen)) < 0) {
                perror("accept");
                exit(EXIT_FAILURE);
            }
            printf("OpenOCD re-connected!\n");
            continue;
        }

        char cmd = buffer[0];
        if (cmd >= '0' && cmd <= '7') {
            // '0'-'7': 写入 TDI, TMS, TCK
            // bit 0: TDI
            // bit 1: TMS
            // bit 2: TCK
            int val = cmd - '0';
            top->io_jtag_TDI = (val >> 0) & 1;
            top->io_jtag_TMS = (val >> 1) & 1;
            top->io_jtag_TCK = (val >> 2) & 1;
            
            // 立即评估模型以反映 JTAG 时钟变化
            top->eval();
            
            // 检测 LED 变化并打印
            if (top->io_led1 != prev_led1 || top->io_led2 != prev_led2) {
                printf("[Sim] LED Update -> LED1: 0x%02x, LED2: 0x%02x\n", top->io_led1, top->io_led2);
                prev_led1 = top->io_led1;
                prev_led2 = top->io_led2;
            }
            
            // 注意：这里不需要切换系统时钟 (top->clock)
            // 因为 JTAG 逻辑是由 io_jtag_TCK 驱动的。
            // 然而，如果有任何逻辑是由系统时钟驱动的，我们可能需要 tick 它。
            // 在我们的设计中，JTAG 逻辑使用 withClock(TCK)，所以当 TCK 变化时 eval() 就足够了。
            
        } else if (cmd == 'R') {
            // 读取 TDO
            char tdo = top->io_jtag_TDO ? '1' : '0';
            send(new_socket, &tdo, 1, 0);
        } else if (cmd == 'Q') {
            break;
        } else if (cmd == 'B' || cmd == 'b') {
            // Blink on/off - 忽略
        } else if (cmd == 'r' || cmd == 's') {
            // Reset - 忽略或实现 TRST
        }
    }

    top->final();
    delete top;
    return 0;
}
