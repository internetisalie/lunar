---
id: "BUG-17"
title: "redisIntegrationTest fails to load its classes, which also fails build on the builder"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-17: `redisIntegrationTest` fails to load its classes

Test infrastructure only; users are not affected.

## 1. Reproduction
At `c9eb7512`, on the libvirt builder:

```bash
~/.cache/claude-scratch/lunar/bin/builder-run libvirt redisIntegrationTest --rerun --no-build-cache
```

`builder-run libvirt build` reaches the same failure.

## 2. Expected vs Actual Behavior
- **Expected**: the three Redis integration classes run against Docker.
- **Actual**: `3 tests completed, 3 failed`, and `build` ends `BUILD FAILED in 1m 19s` before `:test`.
  The log prints, for `RedisDebugIntegrationTest`, `RedisFunctionsIntegrationTest` and
  `RedisIntegrationTest`:

  ```text
  RedisIntegrationTest > initializationError FAILED
      java.lang.NoClassDefFoundError at ClassLoader.java:-2
          Caused by: java.lang.ClassNotFoundException at BuiltinClassLoader.java:580
  ```

  The missing class is only in the test XML,
  `build/test-results/redisIntegrationTest/TEST-net.internetisalie.lunar.redis.RedisIntegrationTest.xml`:

  ```text
  <failure message="java.lang.NoClassDefFoundError: com/intellij/openapi/Disposable" type="java.lang.NoClassDefFoundError">
  Caused by: java.lang.ClassNotFoundException: com.intellij.openapi.Disposable
  ```

## 3. Context / Environment
- **Relevant Files**: `build.gradle.kts` (`redisIntegrationTest` source set and task),
  `src/redisIntegrationTest/`
- **Other Notes**: `:integrationTest` passes alone on the same tree. Docker is available on the
  builder (`docker ps` works; `builder` is in the `docker` group). Found while planning MAINT-50,
  whose gate omits this task until this is fixed. `.claude/agents/reviewer.md:26` tells reviewers
  `build` must be clean, which cannot hold until then.

## 4. Root Cause
The `redisIntegrationTest` source set in `build.gradle.kts` never had the platform on its runtime
classpath. `sourceSets.test.get().runtimeClasspath` does not contain the IDE jars, because IJPGP adds
them to the `test` task's own classpath. `RespClient implements Disposable`, so every class loading
it needs `com.intellij.openapi.Disposable`.

Until `94376e48` (MAINT-21) the platform arrived by accident: `ide-starter` was on
`testImplementation`, and `redisIntegrationTestImplementation` extends that, so its transitive 253
platform jars reached this task. MAINT-21 moved `ide-starter` to `integrationTestImplementation`
because those jars shadowed the real platform in `test` (DR-03). Its design §2.6.3 called the loss
harmless, reasoning only about imports of `com.intellij.ide.starter`.

| Commit | Run | Result | Task classpath |
|--------|-----|--------|----------------|
| `163e81fb` (before MAINT-21) | `redisIntegrationTest --rerun --no-build-cache` | `BUILD SUCCESSFUL`, XML 6 + 2 + 2 | 556 entries, with `ide-core-253.29346.240.jar` and others |
| `94376e48` (MAINT-21) | same | `BUILD FAILED`, `Disposable` on all three | not dumped |
| `4a49a766` (main) | same | `BUILD FAILED`, `Disposable` on all three | 22 entries, no platform jar; `test` has 1238 |

The comment at `build.gradle.kts:483` was also wrong. Nothing names the task, but
`build -x koverVerify --dry-run` schedules neither `:redisIntegrationTest` nor `:integrationTest`, and
`koverVerify --dry-run` alone schedules both, with `:test`. They reach `build` through Kover.

## 5. Fix Strategy
- Add `sourceSets.test.get().compileClasspath` to the source set's `runtimeClasspath`. Its
  `compileClasspath` already contains it, which is why the set compiled. This supplies the target
  platform's jars. Restoring `ide-starter` here would bring back the 253 jars MAINT-21 removed.
- Keep the task in `build`, and correct both comments to say how it gets there.
- Per MAINT-50 design §4.4, add the task to `unit_gate.sh integration`. Each task carries its own
  `--rerun`, a Gradle task option that binds to the task before it.

## 6. Test Strategy
The existing tests are the regression test: at `4a49a766` the three classes fail to initialise, and
after the fix the XML reports the same 10 test methods as `163e81fb`, each run against Docker.
`test_unit_gate.py` pins the new `integration` arguments.
