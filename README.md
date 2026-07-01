# Better Trackpad

A Minecraft Fabric mod that remaps Mac trackpad zones and gestures into configurable mouse actions — no macOS system permissions required.

## Requirements

- macOS (Apple Silicon or Intel)
- Minecraft 26.1.x
- Fabric Loader 0.19.3+
- [Mod Menu](https://modrinth.com/mod/modmenu)
- [Cloth Config](https://modrinth.com/mod/cloth-config)

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.1
2. Download the latest `.jar` from [Releases](https://github.com/proxsyi/better-trackpad/releases)
3. Drop it into your mods folder along with Mod Menu and Cloth Config
4. Launch Minecraft

## How It Works

Better Trackpad divides your trackpad into horizontal zones:

    ┌──────────────────────────────────┐
    │  Left Zone  │  Deadzone  │  Right  │
    │  (x < 0.45) │            │ (x>0.55)│
    └──────────────────────────────────┘

| Gesture | Default Action |
|---|---|
| 1-finger tap — left zone | Left Click |
| 1-finger tap — right zone | Right Click |
| 2-finger tap | Middle Click |
| Physical click | Zone-aware (same mapping) |

All bindings are fully remappable. Works with or without macOS tap-to-click.

## Configuration

Open **Esc → Mods → Better Trackpad → Config**:

- **Bindings tab** — assign Left Click, Right Click, Middle Click, or None to each gesture
- **Configuration tab** — toggle the mod on/off, adjust left/right zone thresholds

Settings persist to `config/better-trackpad.json`.

## How It's Built

- Hooks into Minecraft's native macOS window via JNA + Objective-C runtime
- Reads raw `NSTouch` events directly — no Accessibility or Input Monitoring permissions
- Intercepts GLFW mouse button callbacks to redirect system clicks into the correct action
- Compatible with macOS tap-to-click enabled or disabled

## Building from Source

    ./gradlew build

Requires Java 25 (Temurin recommended). Output jar is in `build/libs/`.

## License

All Rights Reserved — free to download and include (e.g. in modpacks), just give proper credit. Want to modify it? Ask first.

**Discord:** @proxsyi
