# TempleMaps

Run x86 operating systems on the Hytale world map. This plugin wraps the JPC x86 PC emulator to run Windows 95, FreeDOS, and other x86 OSes, rendering the output to the in-game map display using the VideoMaps infrastructure.

## How It Works

TempleMaps uses the h0MER247 JPC emulator - a pure Java implementation of an x86 PC. It interprets x86 machine code instruction-by-instruction entirely within the JVM, allowing unmodified disk images to boot and run on a Hytale server.

```
┌─────────────────────────────────┐
│  Windows 95 / FreeDOS / etc.    │  ← Real x86 OS (unmodified .img/.iso)
├─────────────────────────────────┤
│     JPC x86 Emulator (Java)     │  ← Translates x86 → JVM execution
├─────────────────────────────────┤
│     Hytale Server (JVM)         │  ← Host environment
├─────────────────────────────────┤
│     TempleMaps Plugin           │  ← Bridges emulator ↔ VideoMaps
└─────────────────────────────────┘
```

## Features

- Full x86 PC emulation (Intel 386/486) via JPC
- 640x480 VGA display at 30 FPS
- Support for HDD images (.img) and CD-ROM images (.iso)
- 8 input modes for comprehensive keyboard/mouse control
- Chat commands for typing and direct key input
- Auto-configured RAM based on OS (64MB-512MB)

## Supported Operating Systems

| OS | Image Type | RAM | Notes |
|----|------------|-----|-------|
| Windows 95 | .img (HDD) | 480MB | Max RAM due to Win95 bug |
| Windows 98 | .img (HDD) | 512MB | |
| FreeDOS | .img/.iso | 64MB | Lightweight |
| Other x86 | .img/.iso | 256MB | YMMV |

## Input Modes (Hotbar Slots 1-8)

Switch between modes by selecting different hotbar slots:

| Slot | Mode | W | A | S | D | Jump (Space) |
|------|------|---|---|---|---|--------------|
| **1** | Mouse | Cursor Up | Cursor Left | Cursor Down | Cursor Right | Left Click |
| **2** | Arrow Keys | ↑ | ← | ↓ | → | Enter |
| **3** | System | ESC | F1 | F5 | F10 (Menu) | Space |
| **4** | Windows | Alt+M (Max) | Alt+V (Tile V) | Alt+H (Tile H) | Ctrl+Alt+N (Next) | Ctrl+B (Border) |
| **5** | Zoom | Zoom In | Scroll Left | Zoom Out | Scroll Right | Recenter |
| **6** | Terminal | Ctrl+Alt+T (New) | Tab | Shift+Tab | Ctrl+M (Menu) | Enter |
| **7** | Text Nav | Page Up | Home | Page Down | End | Space |
| **8** | Modifiers | Toggle Shift | Toggle Ctrl | Toggle Alt | Release All | Right Click |

## Commands

```
/temple list                - List available disk images
/temple start <image>       - Start OS (e.g., win95, freedos)
/temple stop                - Stop the emulator
/temple join                - Join as a player
/temple leave               - Leave the session
/temple status              - Show emulator status

/temple type <text>         - Type text as keyboard input
/temple key <keyname>       - Send specific key (ESC, ENTER, F1-F12, etc.)
/temple click               - Left click at cursor position
/temple click times <n>     - Click n times (1-10)
/temple mouse <x> <y>       - Move mouse to absolute position
/temple cli <command>       - Type command and press Enter
```

### Key Names for /temple key

- **Basic**: ESC, ENTER, SPACE, TAB, BACKSPACE, DELETE, INSERT
- **Function**: F1-F12
- **Navigation**: UP, DOWN, LEFT, RIGHT, PAGEUP, PAGEDOWN, HOME, END
- **Modifiers** (toggles): SHIFT, CTRL, ALT
- **Special**: CTRLALTDEL (Ctrl+Alt+Delete)

## Setup

1. Build the plugin: `./gradlew build`
2. Place disk images in `mods/SSquadTeam_TempleMaps/`
   - Windows 95: `win95.img`
   - FreeDOS: `freedos.img` or `freedos.iso`
3. Ensure `et4000.bin` (VGA BIOS) is in `data/bios/`
4. Load the plugin on your Hytale server
5. Use `/temple list` to see available images
6. Use `/temple start <image>` to boot

## Technical Details

- **Resolution**: 640x480 VGA (Tseng ET4000)
- **Frame Rate**: 30 FPS
- **CPU**: Intel 386/486 emulation via JPC
- **RAM**: Auto-configured (64MB-512MB)
- **Boot**: HDD or CD-ROM based on image extension
- **BIOS**: Bochs BIOS + SeaBIOS

## Dependencies

- [TaleLib](https://github.com/ssquadteam/TaleLib) - Plugin framework
- [VideoMaps](https://github.com/ssquadteam/VideoMaps) - Map rendering infrastructure

## Credits & Licenses

### jPC (Java PC Emulator)

This project includes source code from [jPC](https://github.com/h0MER247/jPC), an x86 PC emulator written in Java by h0MER247.

**License: GNU General Public License v2.0 (GPLv2)**

JPC is free software licensed under the GPLv2. If you distribute this plugin with JPC source included, you must:
- Include the GPLv2 license text
- Make the source code available
- License derivative works under GPLv2

### BIOS

The BIOS files used are from the Bochs project (LGPL) and Plex86/Bochs VGA BIOS (LGPL).
