# SigmaJelloBootstrap-Reborn

A clean-room, open-source reimplementation of the historical **Sigma Jello Bootstrap** launcher UI and bootstrap flow.

## Goals

- Recreate the compact 580x150 Sigma Jello Bootstrap experience.
- Preserve the original interaction model where it is useful.
- Reimplement all code from scratch rather than redistributing or modifying the archived prelauncher binary.
- Keep bootstrap/update/launch logic modular so modern endpoints and runtimes can be substituted safely.

## Current status

The first launcher shell is implemented:

- 580x150 non-resizable Swing window
- black background and historical fixed-position layout
- Legacy target: `juzibujiji/SigmaClient` (Java 17)
- Modern target: `Zis30axs/Sigma-Modern` (Java 25)
- custom rounded Play button
- status text at the upper right
- progress mode replacing the selector/button during launch
- Java 8-compatible bootstrap code

The current Play action demonstrates the launch-state transition only. Downloading releases, runtime selection, verification, updating, and spawning the selected client are the next milestone.

The repository intentionally does **not** redistribute the archived Sigma prelauncher binary or historical third-party artwork. The temporary text wordmark in the UI is original project code and can later be replaced by an appropriately licensed asset.

## Architecture direction

The bootstrap remains independent from either client codebase. Legacy and Modern are separate release channels rather than branches of one repository. The future update layer will consume release metadata and launch the selected distribution with its required Java runtime.

## Historical reference

The archived `SigmaJelloPrelauncher.jar` is used only as a behavioral and visual reference for clean-room reimplementation. It is **not** included in this repository.

See [`docs/reference.md`](docs/reference.md).

## License

New source code in this repository is licensed under the GNU GPL v3. Historical Sigma names, logos, artwork, binaries, and other third-party assets are not relicensed by this project. See [`NOTICE.md`](NOTICE.md).
