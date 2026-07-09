# Toolchain feed pins — how they're maintained

`lunar-toolchain-feed.json` ships with **real** `sha256` + `size` pins for every download-based
source and asset. They were computed by fetching each artifact and hashing it; the PUC-Lua
tarballs were additionally **cross-checked against the SHA-256 checksums published at
<https://www.lua.org/ftp/>** (design §4.2 risk-6 safeguard). The single git source (LuaJIT)
carries an empty `sha256` on purpose — the git strategy never reads it.

`LuaToolchainFeedTest` enforces that every download-based `sha256` is a real 64-hex lowercase
string (the old `TODO-PIN` sentinel is no longer accepted); the download/verify path (Phase 2)
rejects any artifact whose bytes don't match its pin.

## Updating a pin (adding/bumping a version)

Follow the design §4.2 "Feed update procedure":

```
curl -fL <url> -o /tmp/a && sha256sum /tmp/a && stat -c%s /tmp/a
```

Fill the resulting `sha256` / `size` into the version's `source` or `asset` entry. For PUC Lua,
cross-check the SHA-256 against <https://www.lua.org/ftp/> before committing. Only ship versions
that actually exist upstream — the dense-range trap (e.g. luarocks skips 3.1.4 / 3.2.2–3.2.4 /
3.3.2–3.3.3) will 404 at provision time; a quick way to bulk-recompute and catch phantoms is to
fetch every feed URL, hash it, and refuse to write on any 404/mismatch.
