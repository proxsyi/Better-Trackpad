# Changelog

All notable changes to Better Trackpad are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial Fabric mod scaffold for Minecraft 26.1 (Java 25, split client/common sources).
- Project `.gitignore` and changelog.
- macOS trackpad touch reader hooked to Minecraft's window via JNA + NSTouch (no system permissions); reads finger count and per-finger position.
