# Changelog

All notable changes to Better Trackpad are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-06-30

### Added
- Zone-based tap detection: 1-finger left/right and 2-finger tap mapped to configurable actions
- Physical click zone-awareness (right zone → right click, 2-finger press → middle click)
- GLFW mouse button intercept redirects macOS tap-to-click without double-firing
- Mod Menu integration with Bindings tab (first) and Configuration tab
- Per-gesture action assignment (LEFT_CLICK, RIGHT_CLICK, MIDDLE_CLICK, NONE)
- Config persistence via config/better-trackpad.json
- macOS trackpad hook via JNA + NSTouch on Minecraft's native window — no system permissions required
- Cloth Config UI with zone threshold sliders and enabled toggle
