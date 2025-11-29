{
  description = "JTAG Daisy Chain Experiments Development Environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            verilator
            python3
            mill
            coursier
            metals
            gnumake
            openocd
            inetutils
            pkgsCross.riscv32-embedded.buildPackages.gcc
            pkgsCross.riscv32-embedded.buildPackages.binutils
            gdb  # 支持多架构，使用 "set arch riscv:rv32" 或 target remote 自动识别
          ];

          shellHook = ''
            echo "JTAG Daisy Chain Experiments Development Environment"
            echo "Available tools:"
            echo "  - Verilator: $(verilator --version | head -n1)"
            echo "  - Python: $(python3 --version)"
            echo "  - Mill: $(mill --version 2>/dev/null || echo 'installed')"
            echo "  - Coursier: $(cs --version 2>/dev/null || echo 'installed')"
            echo "  - GNU Make: $(make --version | head -n1)"
            echo "  - OpenOCD: $(openocd --version 2>&1 | head -n1)"
            echo ""
            echo "Run 'make help' to see available targets"
          '';
        };
      }
    );
}
