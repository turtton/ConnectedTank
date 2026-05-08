{
  description = "A basic flake with a shell";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  inputs.systems.url = "github:nix-systems/default";
  inputs.flake-utils = {
    url = "github:numtide/flake-utils";
    inputs.systems.follows = "systems";
  };
  inputs.git-hooks = {
    url = "github:cachix/git-hooks.nix";
    inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      git-hooks,
      ...
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        libraries =
          with pkgs;
          lib.makeLibraryPath [
            libpulseaudio
            libGL
            udev
            flite
            libx11
            libxcursor
            libxi
            libxext
            libxxf86vm
            libxrandr
          ];
      in
      {
        formatter = pkgs.nixfmt-tree;

        checks = {
          pre-commit-check = git-hooks.lib.${system}.run {
            src = ./.;
            hooks = {
              stonecutter-version = {
                enable = true;
                name = "Stonecutter vcsVersion guard";
                entry = "${pkgs.bash}/bin/bash -c 'if ! grep -q '\"'\"'stonecutter active \"1.21.8-fabric\"'\"'\"' stonecutter.gradle.kts; then ${pkgs.gnused}/bin/sed -i '\"'\"'s/stonecutter active \".*\"/stonecutter active \"1.21.8-fabric\"/'\"'\"' stonecutter.gradle.kts; echo \"Fixed: stonecutter active version reset to 1.21.8-fabric (vcsVersion). Please re-commit.\"; exit 1; fi'";
                files = "stonecutter\\.gradle\\.kts$";
                language = "system";
                pass_filenames = false;
              };
            };
          };
        };

        devShells.default = pkgs.mkShell {
          inherit (self.checks.${system}.pre-commit-check) shellHook;
          packages =
            with pkgs;
            [
              bashInteractive
              xrandr
              xorg-server
              pinact
              zizmor
            ]
            ++ self.checks.${system}.pre-commit-check.enabledPackages;
          env.LD_LIBRARY_PATH = libraries;
        };
      }
    );
}
