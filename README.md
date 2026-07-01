# Better Trackpad

A Minecraft Fabric mod that remaps Mac trackpad zones and gestures into configurable mouse actions — no macOS system permissions required.

## Requirements

- macOS (Apple Silicon or Intel)
- Minecraft 26.1.x
- Fabric Loader 0.19.3+
- [Fabric API](https://modrinth.com/mod/fabric-api)

### Optional
- [Mod Menu](https://modrinth.com/mod/modmenu)
- [Cloth Config](https://modrinth.com/mod/cloth-config)

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.1
2. Download the latest `.jar` from [Releases](https://github.com/proxsyi/better-trackpad/releases)
3. Drop it into your mods folder along with Mod Menu and Cloth Config
4. Launch Minecraft

## How It Works

Better Trackpad divides your trackpad into horizontal zones:
```
┌────────────────────────────────────────┐
│  Left Zone  │  Deadzone  │  Right Zone │
│  (x < 0.45) │            │   (x>0.55)  │
└────────────────────────────────────────┘
```
| Gesture | Default Action |
|---|---|
| 1-finger tap — left zone | Left Click |
| 1-finger tap — right zone | Right Click |
| 2-finger tap | Middle Click |
| Physical click | Zone-aware (same mapping) |

Hold a click past a short threshold and it escalates into a continuous hold, so mining blocks and using items behave exactly like a held mouse button. All bindings are fully remappable. Works with or without macOS tap-to-click.

## Configuration

Open **Esc → Mods → Better Trackpad → Config**:

- **Bindings tab** — assign Left Click, Right Click, Middle Click, or None to each gesture
- **Configuration tab** — toggle the mod on/off, adjust left/right zone thresholds

Settings persist to `config/better-trackpad.json`. Set `"debug": true` there to enable verbose diagnostic logging (off by default).

## How It's Built

- Hooks into Minecraft's native macOS window via JNA + Objective-C runtime
- Reads raw `NSTouch` events directly — no Accessibility or Input Monitoring permissions
- Owns the in-game trackpad button so system clicks never leak through as stray attacks
- Layered for portability: a platform touch hook feeds a gesture detector, which drives an actuator — keeping OS input, gesture interpretation, and game actions decoupled so new platforms can be added without touching the core logic
- Compatible with macOS tap-to-click enabled or disabled

## Building from Source

./gradlew build

Requires Java 25 (Temurin recommended). Output jar is in `build/libs/`.

## License

All Rights Reserved — free to download and include (e.g. in modpacks), just give proper credit. Want to modify it? Ask first.

**Discord:** @proxsyi
