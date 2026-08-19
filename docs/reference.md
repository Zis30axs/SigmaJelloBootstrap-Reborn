# Historical UI reference

Observed from the archived Sigma Jello prelauncher binary and preserved screenshots/community reports.

## Window

- Title: `Sigma Jello Bootstrap`
- Size: 580 x 150
- Non-resizable
- Black background
- Java Swing UI

## Ready state

- Logo: x=26, y=28, approximately 221x35 when rendered in the original UI
- Version selector: x=26, y=80, 195x22
- Play button: x=360, y=75, 195x30
- Status text: x=352, y=44, 200x20
- Status text is white and right-aligned
- Typical text includes `Auto Play in 10s..` and `Select version and play!`

## Launch state

- Version selector and Play button are hidden
- A custom progress bar is shown
- Status messages include `Updating Client`, `Updating Runtime`, and `Launching Client`
- The original progress renderer appears to draw a small minimum filled segment even at 0%

## Clean-room rule

Do not copy decompiled source code into this repository. Recreate behavior from independently documented observations and tests.
