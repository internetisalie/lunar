---
id: "BUG-446"
title: "Docker provisioning has never worked in the IDE — `launchDocker` double-consumes the process stream, so the run fails and the container leaks"
type: "bug"
parent_id: "BUG"
status: "done"
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

*Both of this section's open questions — the A/B fork and the readiness hypothesis — were settled by
measurement on the builder (2026-08-21), recorded below.*

### 5a. The container id: capture through the platform, never alongside it

**Chosen: option B's shape, via the house idiom rather than `ExecUtil`.**
`LuaToolExecutionService.capture` already wraps `CapturingProcessHandler`: the platform stays the
sole reader and hands back stdout, stderr and the exit code. That deletes the two-consumer shape
instead of arranging for one consumer to win, and it adds no new dependency — `ExecUtil` has no
precedent in this repo, while `LuaToolExecutionService` is the established vehicle in
`toolchain/exec`.

`docker run --rm -d` prints the id and exits, and the container outlives it. The `OSProcessHandler`
on this path was therefore never doing anything *except* creating the second consumer.

A non-zero exit must **throw**, carrying the captured stderr, rather than yield a blank id. The blank
id is the leak: `stopDockerContainer` returns early on `containerId.isBlank()`, so a `docker run`
that fails for any reason — bad image, daemon down — currently fails with no diagnosis *and* skips
its own cleanup.

### 5b. The readiness gap is real, on BOTH paths — measured, not inferred

This section previously called the gap "consistent with" the 1-second spacing of two log lines,
which was a guess. Measured directly, three runs each:

| path | at the moment `launch` returns today | answers after | why the shapes differ |
| :-- | :-- | :-- | :-- |
| **Docker** (`redis:8`) | **connected, but no reply — 3/3** | 31 / 36 / 70 ms | docker-proxy binds the host port at container-create time, so the TCP connect succeeds while nothing listens inside the container |
| **Local binary** (`redis-server --port N --save ""`) | **connection refused — 3/3** | 15 / 17 / 32 ms | nothing is bound yet at all |

**Neither path was ever ready when `launch` returned.** The consequences differ: the binary path
fails fast with `RespException.Io`, while the Docker path connects and then stalls inside
`RespHandshake.negotiate`. The Docker shape is the worse of the two precisely because **any readiness
check that only asks "can I connect?" reports ready** — a TCP connect is not a valid readiness signal
against a published container port.

So `launch` must not return until the server has answered on the wire, and that holds for
`launchBinary` as much as `launchDocker`. Covering the binary path is deliberate scope, not creep:
it is one call to the same helper, its race is measured rather than assumed, and shipping a fix on
one of two paths that both demonstrably need it is the same omission that produced BUG-446.

**Readiness means *any* reply to a `PING`, not specifically `PONG`.** A server answering
`-NOAUTH Authentication required` is listening, which is the only question being asked here;
authentication is the caller's business. Requiring `PONG` would turn every password-protected server
into a launch timeout.

The gate is modelled as its own production type, `LuaRedisServerReadiness`, rather than a private
helper inside the launcher — it serves both launch paths, and the distinction it encodes (protocol
reply, **not** TCP connect) is the kind that gets quietly undone by a later edit if it lives as three
lines in the middle of `launchDocker`. Testcontainers hits the same trap and answers it differently:
its `HostPortWaitStrategy` runs the port check *inside* the container (`nc -z`), which needs a tool
present in the image and does nothing for a local binary. Asking the server to speak covers both of
our paths with one mechanism and no assumptions about the image.

### 5c. A third leak path, found while fixing the first two

Adding the readiness gate created a window that did not exist before: the container is running, and
`launch` has not yet returned. Anything thrown in that window leaks it, because nothing outside the
launcher holds a reference with which to stop it.

**The case that matters is cancellation.** The readiness poll suspends in `delay`, so cancelling a
run configuration resumes it with a `CancellationException` — an ordinary, expected event that would
have leaked a container every time.

Found empirically rather than by reasoning: a `redis:8` container was left running on the builder
after a test failed *between* the container starting and `launch` returning (the harness was calling
`launch` on the EDT, which the platform rejects). The leak was the interesting part, not the harness
bug. `readyServer` now stops the server on **any** throwable and rethrows it untouched.

## 6. Test strategy

| test | asserts | where |
| :-- | :-- | :-- |
| `launchDocker` returns a non-blank container id | the id is actually read — the defect itself | unit, via seams |
| `LaunchedServer.stop` issues `docker rm -f <id>` | the leak, at the seam | unit, via seams |
| a failing `docker run` throws, carrying its stderr | the silent blank-id path | unit, via seams |
| `launch` does not return before the server replies | the readiness gap, on both paths | unit, via seams |
| a real container: non-blank id, live `PING`, removed by `stop` | all of the above, for real | `test -PwithDocker` |

**`LaunchSeams` is widened to cover process execution and readiness** — the two things it did not
cover, and therefore the two things that broke. It keeps `resolveToolPath` / `resolveDockerPath` /
`allocatePort` and gains `execute` and `awaitReady`. Five seams sits past the engineering contract's
3-argument cap, which the contract itself answers: it directs surplus parameter state into "a
dedicated configuration or execution context class", and `LaunchSeams` is exactly that class.

**The real-Docker test CALLS `LuaRedisServerLauncher`.** `RedisIntegrationTest`,
`RedisFunctionsIntegrationTest` and `RedisDebugIntegrationTest` all start their own containers
through a raw `ProcessBuilder`, one of them under a KDoc claiming consistency with the launcher —
that mirroring is what manufactured the appearance of coverage (§4). Adding a fourth mirror would
repeat the mistake this bug exists to record.

It lands in `src/test` as `LuaRedisServerLauncherDockerTest`, **not** in the `redisIntegrationTest`
source set where the Docker suites live, and the reason is worth recording because it is not
obvious. Driving the launcher needs a real `Project`, so the test is a `BasePlatformTestCase`, and
only the IJPGP-configured `test` task supplies what that needs. A hand-registered `Test` task fails
in three escalating ways — `UsefulTestCase.runBare` asserts on `--add-opens=java.base/java.lang`,
then `PathManager.getHomePath` wants `idea.home.path`, then the forked JVM dies outright because
`java.system.class.loader` has to be set at fork time and cannot be copied in a `doFirst`. Only the
first is a one-line fix; `build.gradle.kts` already records the same limitation against the perf
suites ("a standalone Test task fails at platform fixture init"). It is kept out of the routine gate
by name, exactly as the corpus sweeps are: `./gradlew test -PwithDocker`.

## 7. Notes

- **Severity**: Docker provisioning is a documented, advertised capability that does not work and
  leaks a container per attempt. Local-binary provisioning is unaffected.
- BUG-381 is unaffected and correctly closed: its scope was the settings UI, and the UI now
  correctly persists the choice and drives the launcher — the live pass proved the launcher is
  *invoked* with the right command line. What happens after that is this bug.
