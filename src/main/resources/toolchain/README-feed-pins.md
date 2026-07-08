# Toolchain feed pins — MUST be filled before release

`lunar-toolchain-feed.json` ships with every `"sha256"` set to the sentinel string
**`"TODO-PIN"`** and every `"size"` set to `0`. These are deliberate placeholders, not real
checksums: this environment has no network and CI never downloads, so real SHA-256/size pins
cannot be computed here (TOOLING-04 Phase 1).

**Before the live provision / release** a maintainer MUST replace every `TODO-PIN` /
`size: 0` with the real values, following the design §4.2 "Feed update procedure":

```
curl -fL <url> -o /tmp/a && sha256sum /tmp/a && stat -c%s /tmp/a
```

For PUC Lua, cross-check the SHA-256 against the checksums published at
<https://www.lua.org/ftp/>. `LuaToolchainFeedTest` asserts each `sha256` is either a real
64-hex lowercase string **or** exactly `TODO-PIN`, so a typo in a real pin's length fails the
build; the download/verify path (Phase 2) will reject `TODO-PIN` against a real artifact.
