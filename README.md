# SigmaJelloBootstrap-Reborn

A clean-room, open-source reimplementation of the historical **Sigma Jello Bootstrap** launcher UI and bootstrap flow.

## Goals

- Recreate the compact 580x150 Sigma Jello Bootstrap experience.
- Preserve the original interaction model where it is useful.
- Reimplement all code from scratch rather than redistributing or modifying the archived prelauncher binary.
- Keep bootstrap/update/launch logic modular so modern endpoints and runtimes can be substituted safely.

## Current status

Project scaffold only. The first implementation target is the original launcher shell:

- 580x150 non-resizable Swing window
- black background
- logo area at the upper left
- status text at the upper right
- version selector at the lower left
- Play button at the lower right
- progress mode replacing the selector/button during launch

## Historical reference

The archived `SigmaJelloPrelauncher.jar` is used only as a behavioral and visual reference for clean-room reimplementation. It is **not** included in this repository.

See [`docs/reference.md`](docs/reference.md).

## License

New source code in this repository is licensed under the MIT License. Historical Sigma names, logos, artwork, binaries, and other third-party assets are not relicensed by this project. See [`NOTICE.md`](NOTICE.md).
