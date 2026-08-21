---
id: "BUG-446"
title: "Docker provisioning has never worked in the IDE — `launchDocker` double-consumes the process stream, so the run fails and the container leaks"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-446: `launchDocker` reads a stream the platform is already pumping

*Found 2026-08-21 during BUG-381's VNC pass — the first time `LuaRedisServerLauncher.launchDocker`
has been run for real, as far as the evidence goes (§4).*

## 1. Reproduction

1. Set the project's **Platform target** to Redis.
2. Create a Redis connection with **Server: Docker image**, `redis:8` (BUG-381's control).
3. Open a `.lua` script — `return redis.status_reply("ok")` — and *Run 'Redis Script: …'*.

## 2. Expected vs actual

- **Expected**: the container starts, the script's reply renders in the console, and the container
  is removed when the session ends.
- **Actual**, measured live on the builder:

  ```
  19:06:22 INFO  LuaRedisServerLauncher - Launching Redis Docker container:
                 /usr/bin/docker run --rm -d -p 40513:6379 redis:8
  19:06:23 INFO  LuaRedisRunProfileState - Redis run failed: Stream closed
  ```

  The console shows **`(no reply)`**, and the container is **still running** minutes later
  (`docker ps` → `redis:8  Up About a minute`). It had to be removed by hand.

## 3. Root cause

[`LuaRedisServerLauncher.launchDocker`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisServerLauncher.kt)
starts the platform's stream pump and then reads the same stream itself:

```kotlin
val handler = OSProcessHandler(commandLine)
ProcessTerminatedListener.attach(handler)
handler.startNotify()                      // platform reader threads begin draining process.inputStream
val containerId = readContainerId(handler) // ... and then we read process.inputStream directly
```

```kotlin
private fun readContainerId(handler: OSProcessHandler): String {
    handler.waitFor(5_000)
    return handler.process.inputStream.bufferedReader().readLine().orEmpty().trim()
}
```

`startNotify()` hands `process.inputStream` to `BaseOSProcessHandler`'s reader threads. Reading it
again directly is a second consumer of a single stream, and the loser gets nothing or an
`IOException: Stream closed`. **One defect, both symptoms:**

- the `readLine()` fails → `containerId` is `""` → `stopDockerContainer` returns early on
  `containerId.isBlank()` → **the container is never removed**;
- the failure propagates out of `launch()` → `LuaRedisRunProfileState` reports
  `Redis run failed: Stream closed` and the console renders `(no reply)`.

`launchBinary` does not have this defect: it never reads the stream, because a server binary has no
id to read back.

## 4. Why nothing caught it — the coverage claim was wrong

BUG-381 §3 recorded "Launcher … done" and "Integration tests: `src/redisIntegrationTest/…`
exercises real `redis:8` / `valkey/valkey:8` Docker provisioning", and that pairing is what made the
launcher look covered. **It is not.** `RedisIntegrationTest` and `RedisFunctionsIntegrationTest`
start containers **themselves**, with a raw `ProcessBuilder("docker", "run", …)`, and their own
KDoc says the command is *"consistent with `LuaRedisServerLauncher`"* — they **mirror** it. Neither
file references the class.

`TestLuaRedisServerLauncher` constructs the launcher through its `LaunchSeams` internal constructor
and asserts the **command-line shape** (TC-LAUNCH-1). The shape is right; the process handling
around it is what breaks, and no test reaches it.

So the launcher's real Docker path had **no coverage at all**, and until BUG-381 shipped a UI there
was also no way to reach it from the IDE. That combination is why a defect on the first line of the
happy path survived REDIS-01 through REDIS-06.

## 5. Fix strategy

**Do not read `handler.process.inputStream` after `startNotify()`.** Two candidates, to be chosen on
measurement rather than taste:

- **A — capture through the handler.** Attach a `ProcessAdapter`/`CapturingProcessAdapter` before
  `startNotify()` and take the container id from the captured stdout. Keeps the platform as the sole
  reader, which is the invariant that was broken.
- **B — don't use a handler for the id.** `docker run -d` is a short synchronous command; run it with
  `ExecUtil.execAndGetOutput(GeneralCommandLine)` and use the handler only for the container's
  lifetime, if at all. Simpler, and it removes the two-consumer shape entirely rather than arranging
  for one consumer to win.

Fix the **readiness gap** at the same time, or state why not: `docker run -d` returns as soon as the
container is *created*, not when Redis accepts connections. Even with the id read correctly, the
client may connect before the server is listening. The 1-second gap between the two log lines above
is consistent with that being the *next* failure once this one is fixed.

## 6. Test strategy

| test | asserts |
| :-- | :-- |
| `launchDocker` returns a non-blank container id | the id is actually read — this is the defect |
| `LaunchedServer.stop` issues `docker rm -f <id>` for that id | the leak, at the seam |
| a launched server accepts a RESP `PING` before `launch` returns | the readiness gap, if fixed |

The first two are reachable through the existing `LaunchSeams` **only if the seam is widened to
cover process creation** — today it covers tool/docker path resolution and port allocation, and the
`OSProcessHandler` is constructed directly, which is precisely the untested part. Widening that seam
is most of this fix's test cost and should be planned deliberately.

**A real-Docker test belongs in `src/redisIntegrationTest`, calling `LuaRedisServerLauncher`** rather
than mirroring its command line — that mirroring is what created the appearance of coverage.

## 7. Notes

- **Severity**: Docker provisioning is a documented, advertised capability that does not work and
  leaks a container per attempt. Local-binary provisioning is unaffected.
- BUG-381 is unaffected and correctly closed: its scope was the settings UI, and the UI now
  correctly persists the choice and drives the launcher — the live pass proved the launcher is
  *invoked* with the right command line. What happens after that is this bug.
