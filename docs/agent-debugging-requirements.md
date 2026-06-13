---
id: "AGENT-DEBUG"
title: "Agent Debugging Requirements"
type: "guide"
status: "done"
priority: "medium"
folders:
  - "[[features]]"
---

# Agent Debugging Requirements

Requirements for Claude Code (or a similar terminal-based agent) to autonomously debug the
Lunar plugin against the containerized IDE in `docker/`. Written by Claude Code; describes
what the agent needs from this environment, what the current setup already provides, and
the gaps to close.

## Overview

The agent works the container through two channels, and both are required for a full
debug loop:

| Channel | Used for | Mechanism |
|---|---|---|
| Terminal | builds, plugin install, JDWP/`jdb`, log reading, process inspection | `docker exec lunar-ide ...` |
| GUI | reproducing UI-triggered bugs, visual confirmation, driving the IDE | VNC MCP server (screenshot + mouse/keyboard over VNC) |

The intended loop:

1. Edit plugin source on the host (normal file tools).
2. Build and install into the container: `./docker-helper.sh setup-plugin`.
3. Launch/restart the IDE inside the container, with JDWP enabled when stepping is needed.
4. Reproduce the bug via VNC (screenshots to see, synthetic input to act).
5. Read results from the terminal side: `jdb` breakpoints/stacks, `idea.log`, thread dumps.

No human is required in the loop, but a human can watch or intervene through their own
VNC viewer — agent and human share the same desktop.

## Requirements

### 1. VNC MCP server registered with Claude Code

The VNC MCP server (`~/Documents/src/mcp/vnc-mcp-server`) must be registered with Claude
Code, not only with Gemini/Antigravity. One-time setup:

```bash
claude mcp add vnc --scope user \
  --env VNC_HOST=localhost \
  --env VNC_PORT=5900 \
  --env VNC_PASSWORD=vncpass \
  -- node /home/mini/Documents/src/mcp/vnc-mcp-server/dist/index.js
```

MCP servers connect at session start. The container (and its VNC server) must be running
**before** the Claude Code session starts, or the VNC tools will be absent/broken for the
whole session.

### 2. VNC reachable from the host

**Likely gap.** `vnc-start.sh` starts `x11vnc -listen localhost`, which binds to
127.0.0.1 *inside the container's network namespace*. With bridge networking and
`-p 5900:5900`, Docker forwards host connections to the container's eth0 IP — where
nothing is listening. Verify with a VNC client from the host; if it cannot connect:

```bash
# vnc-start.sh: listen on all container interfaces (only the published port is exposed)
x11vnc -display ${DISPLAY} -forever -rfbauth ~/.vnc/passwd -listen 0.0.0.0 -rfbport ${VNC_PORT} &
```

and restrict the published port to the host only (defense in depth, since the password is
weak and checked into docs):

```bash
# docker-helper.sh run: bind to loopback instead of all host interfaces
-p 127.0.0.1:5900:5900
```

### 3. Stable container identity — already met

The fixed name `lunar-ide` lets the agent script `docker exec` calls without discovering
container IDs. Keep it.

### 4. Adequate display resolution — already met

Xvfb runs at 1920x1080x24. JetBrains IDEs at lower resolutions truncate menus and panels,
which breaks screenshot-driven interaction. Treat 1920x1080 as the minimum; do not shrink
it to improve VNC performance when an agent is the primary consumer.

### 5. JDWP debugging inside the container

For breakpoint/step debugging the agent uses `jdb` via `docker exec`, attaching to the
IDE JVM. Requirements:

- A documented way to launch the IDE with JDWP enabled, e.g. an env toggle in the
  entrypoint that adds
  `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`
  to the IDE VM options (or an `idea64.vmoptions`/`goland64.vmoptions` drop-in under the
  IDE config dir).
- No host port mapping is needed if the agent attaches from inside:
  `docker exec -it lunar-ide jdb -attach localhost:5005`. Publish `-p 127.0.0.1:5005:5005`
  only if a host-side debugger (e.g. a second IDE) should attach too.
- JDK with `jdb` present — already met (image installs `openjdk-21-jdk`, which includes it).

### 6. Plugin build/install cycle from the host

Already met by `./docker-helper.sh setup-plugin` (builds the distribution zip and installs
it into the running container). Requirements on top:

- The cycle must be non-interactive end to end, including the IDE restart that picks up
  the new plugin version. If `setup-plugin` does not restart the IDE, document the restart
  command (kill + relaunch under the existing DISPLAY) so the agent can do it.
- First-run dialogs (trust project, privacy/consent, tips) should be pre-answered in the
  baked-in IDE config where possible — every modal dialog is extra screenshot-and-click
  work for the agent.

### 7. Log access

The agent reads `idea.log` rather than watching the GUI wherever possible. Inside the
container the IDE writes logs under the JetBrains system directory (for GoLand on Linux,
`~/.cache/JetBrains/GoLand<version>/log/idea.log`; container stdout via
`docker logs lunar-ide` covers Xvfb/VNC/IDE startup). Requirement: the exact log path for
the configured IDE should be documented or symlinked to a stable location (e.g.
`/home/lunar/logs/idea.log`) so agent scripts do not need per-IDE path logic.

### 8. Test project availability — already met

`docker-helper.sh run` mounts `$LUNAR_TEST_PROJECT_PATH` (default
`~/Documents/src/lua/test`) at `/home/lunar/test`. The agent edits test-project files from
the host and the container sees changes immediately. Keep the mount read-write.

## Current status

*Verified 2026-06-13 via `docker-helper.sh build` + `LUNAR_DEBUG=1 ./docker-helper.sh run`:
VNC reachable on `127.0.0.1:5900`; `jdb -attach localhost:5005` connects to the live IDE JVM;
the log symlink resolves to `GoLand2026.1/log/idea.log`; `setup-plugin` reinstalls into the
correct IDE dir. The `jdb` workflow is documented in the `jdb-debugger` skill.*

| Requirement | Status |
|---|---|
| VNC MCP server registered with Claude Code | ✅ registered in `~/.claude.json` — note MCP servers connect at **session start**, so start the container *before* the session that uses VNC |
| VNC reachable from host | ✅ entrypoint already listens `0.0.0.0`; dead `vnc-start.sh` (the `-listen localhost` red herring) removed; host ports now bound to `127.0.0.1` |
| Stable container name | ✅ `lunar-ide` |
| 1920x1080 display | ✅ |
| JDWP launch toggle | ✅ `LUNAR_DEBUG=1 ./docker-helper.sh run` → entrypoint adds `-agentlib:jdwp=…:5005` to the IDE VM options |
| `jdb` in image | ✅ (openjdk-21-jdk) |
| Non-interactive plugin install | ✅ `./docker-helper.sh setup-plugin` — rebuild + `docker cp` + reinstall + restart IDE |
| Stable log path | ✅ entrypoint symlinks the IDE log to `/home/lunar/logs/idea.log` |
| Test project mount | ✅ |

## Practical notes for agent sessions

- Prefer the terminal channel whenever both would work — `docker exec` + logs is faster
  and more reliable than screenshot round-trips. Reserve VNC for steps that genuinely
  need the GUI.
- Many plugin bugs reproduce in `BasePlatformTestCase`/integration tests without the
  container at all; the container loop is for behavior that only manifests in a real IDE
  session. See [integration tests](implementation/integration-tests.md).
- VNC interaction is slow and click-precision-sensitive in dense IDE UI. Batch GUI work:
  set up everything possible from the terminal first, then do the minimal GUI sequence.
