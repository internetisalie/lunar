---
id: "BUG-17"
title: "redisIntegrationTest fails to load its classes, which also fails build on the builder"
type: "bug"
parent_id: "BUG"
status: "todo"
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
