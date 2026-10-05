# rslvd Terminal

A standalone Android terminal for [rslvd.net](https://rslvd.net) — a rebranded,
Termux-style environment served from our own package repository.

- Package name **`net.rslvd.debug`** (fixed: every binary in the bootstrap and
  apt repo is compiled with prefix `/data/data/net.rslvd.debug/files/usr`).
- On first launch it downloads the bootstrap for the device's CPU from
  `https://repo.rslvd.net/bootstraps/` (SHA-256 verified) into `$PREFIX`, then
  drops you into `bash -l` with `pkg`/`apt`/`dpkg`, netcat, net-tools, dnsutils,
  nmap, util-linux, nano and `tsu` preinstalled. `pkg install …` pulls from the
  GPG-signed repo at `https://repo.rslvd.net/apt/rslvd-main`.
- Real terminal: native PTY (`forkpty` via JNI) + xterm-256color emulator, so
  `nano`, `vim`, `top`, `less`, readline history and colours work. Extra-keys
  row (ESC/TAB/CTRL/ALT/arrows/Home/End/PgUp/PgDn/Del), pinch to zoom,
  long-press to select/copy, Vol-down = CTRL. The ⋮ menu has Paste, Restart
  shell and Reinstall environment.
- Nothing else: no rslvd.net account, hosts, tunnels or billing — that lives in
  the Play Store app (`net.rslvd.client`, [igaret/rslvd.net](https://github.com/igaret/rslvd.net)).

> Not on Google Play by design: Play's Device & Network Abuse policy forbids
> apps that download and execute native code, which is the whole point here.
> Install from <https://rslvd.net/rslvd-terminal.apk>.

## Build

Requirements: JDK 17, Android SDK 36 + NDK 27.2.12479018 (`ANDROID_HOME` set; the
NDK builds the PTY helper `libtermux.so` for arm64-v8a / armeabi-v7a / x86_64).

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with the keystore described in `keystore.properties`
(see `keystore.properties.example`); without it they fall back to the debug key.

`targetSdk` is pinned to **28** on purpose: from targetSdk 29 Android forbids
executing binaries from the app's data directory (W^X), which would break the
whole environment — the same reason Termux's non-Play build targets 28.

## Layout

| Module | What |
|--------|------|
| `app/` | `MainActivity`, `TerminalScreen` (Compose UI + extra keys), `TerminalHost` (PTY session), `BootstrapInstaller` |
| `terminal-emulator/` | VT100/xterm emulator + JNI PTY helper, vendored from [termux-app](https://github.com/termux/termux-app) (Apache-2.0) |
| `terminal-view/` | `TerminalView` renderer/input handling, vendored from termux-app (Apache-2.0) |

The bootstrap and `.deb` packages themselves are built by
[`scripts/shell-repo`](https://github.com/igaret/rslvd.net/tree/main/scripts/shell-repo)
in the rslvd.net repo (a patched termux-packages build with
`TERMUX_APP__PACKAGE_NAME=net.rslvd.debug`) and deployed to repo.rslvd.net.
