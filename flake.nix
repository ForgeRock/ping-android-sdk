{
  description = "A development environment for the unified-sdk-android project";

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
          buildInputs = [
            pkgs.jdk17
            pkgs.gradle
            pkgs.android-tools
          ];

          # Set up the Android SDK
          ANDROID_HOME = "${pkgs.androidsdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${pkgs.androidsdk}/libexec/android-sdk";

          shellHook = ''
            export GRADLE_USER_HOME=$(mktemp -d)
          '';
        };
      });
}
