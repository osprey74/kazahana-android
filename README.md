[日本語](README.ja.md)

# Kazahana for Android

**A lightweight Bluesky client for Android**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Overview

Kazahana for Android is a native Bluesky client built with Kotlin and Jetpack Compose.
It brings the same lightweight, fast, and simple experience as the [desktop version](https://github.com/osprey74/kazahana) to Android devices.

## Philosophy

Kazahana is designed as a **lightweight companion app** — not a full-featured standalone replacement for the official Bluesky web client.

- **Daily essentials in Kazahana** — Timeline browsing, posting, notifications, search, DMs, and other frequently used operations.
- **Configuration via Bluesky web** — Account management, block/mute list management, and other administrative tasks are left to [bsky.app](https://bsky.app/).

## Tech Stack

| Technology | Purpose |
|------------|---------|
| [Kotlin](https://kotlinlang.org/) | Programming language |
| [Jetpack Compose](https://developer.android.com/develop/ui/compose) | UI framework |
| [AT Protocol](https://atproto.com/) | Bluesky API |

## Requirements

- Android 10 (API 29)+
- Android Studio Ladybug (2024.2) or later

## Development

```bash
# Clone the repository
git clone https://github.com/osprey74/kazahana-android.git

# Open in Android Studio
# File > Open > select the kazahana-android directory
```

## Related Projects

- [kazahana](https://github.com/osprey74/kazahana) — Desktop version (Windows / macOS)
- [kazahana-ios](https://github.com/osprey74/kazahana-ios) — iOS version
- [BSAF Protocol](https://github.com/osprey74/bsaf-protocol) — Bluesky Structured Alert Feed specification

## License

[MIT License](LICENSE)
