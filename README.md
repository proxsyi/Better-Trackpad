# Better Trackpad

> Remap your trackpad zones and gestures to any Minecraft action — no system permissions required.

Better Trackpad is a Fabric mod that reads raw trackpad input directly from Minecraft's window and lets you assign different actions to different parts of your trackpad. Tap the left side to left-click, tap the right side to right-click, two-finger tap to middle-click — all fully remappable.

---

## Features

- **Zone-based taps** — divide your trackpad into left, deadzone, and right zones; tap each to trigger a different action
- **2-finger tap** — assignable independently (defaults to middle click)
- **Physical click zone-awareness** — pressing the trackpad down is also zone-aware
- **Fully remappable** — assign Left Click, Right Click, Middle Click, or None to any gesture
- **In-game config UI** — live configuration via Mod Menu with a Bindings tab and Configuration tab
- **Persistent settings** — saves to `config/better-trackpad.json`
- **No system permissions** — hooks directly into Minecraft's window; no Accessibility or Input Monitoring required
- **Works with macOS tap-to-click** on or off

---

## How It Works

Your trackpad is divided into horizontal zones based on the x position of your touch (0 = left edge, 1 = right edge):

    +---------------------------------------+
    | Left Zone  |  Deadzone  |    Right     |
    | x < 0.45   | 0.45-0.55  |   x > 0.55  |
    +---------------------------------------+

| Gesture | Default |
|---|---|
| 1-finger tap - left zone | Left Click |
| 1-finger tap - right zone | Right Click |
| 2-finger tap | Middle Click |
| Physical press - left zone | Left Click |
| Physical press - right zone | Right Click |
| Physical press - 2 fingers | Middle Click |

Zone thresholds are adjustable in the Configuration tab.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.1
2. Download **Better Trackpad**, [Mod Menu](https://modrinth.com/mod/modmenu), and [Cloth Config](https://modrinth.com/mod/cloth-config)
3. Drop all three jars into your mods folder
4. Launch Minecraft

---

## Configuration

Open **Esc -> Mods -> Better Trackpad -> Config icon**

### Bindings tab
Assign an action to each gesture. Options: Left Click, Right Click, Middle Click, None.

### Configuration tab
- **Enabled** - toggle the mod on/off without removing it
- **Left Zone Max** - right boundary of the left zone (default 0.45)
- **Right Zone Min** - left boundary of the right zone (default 0.55)

Changes are saved automatically when you hit **Save & Quit**.

---

## Platform Support

| Platform | Status |
|---|---|
| macOS (Apple Silicon) | Fully supported |
| macOS (Intel) | Fully supported |
| Windows | Planned |
| Linux | Planned |

The mod is architected for cross-platform trackpad support. macOS is the first fully supported platform.

---

## Building from Source

Requires Java 25 (Temurin recommended).

~~~sh
git clone https://github.com/proxsyi/better-trackpad
cd better-trackpad
./gradlew build
~~~

Output jar: `build/libs/better-trackpad-0.1.0.jar`

---

## License

MIT
