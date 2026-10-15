---
id: "BUG-503"
title: "A LuaCATS description token shared by enough declarations in one file overflows the description index, and indexing that file fails"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-503: the description index writes its values with `DataOutput.writeUTF`, which caps at 64 KB

Found by TARGET-11's prototype (DR-02), enabling Neovim's `runtime/lua` as a definition library.
Blocks TARGET-11. `$S` = `~/.cache/claude-scratch/lunar/target-11`; every run on the libvirt builder
with `test --rerun --no-build-cache`.

## 1. What happens

`LuaDescriptionIndex.Indexer.map` (`src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaDescriptionIndex.kt`)
emits one value per description token per file: every `DescriptionRecord` whose description contains
that token, joined with `|`. Each record carries the file URL. `StringDataExternalizer.save` wrote
the value with `output.writeUTF(value)`, and `java.io.DataOutputStream.writeUTF` throws for an
encoded length above 65535 bytes.

Neovim's `vim/_meta/vimfn.gen.lua` (423,875 bytes, one documented `function vim.fn.*` per Vimscript
function) produces such a value. With the `neovim` entry of TARGET-11 design §2.1 pinned for the
lazy.nvim corpus member (`$S/run3-library.log`, `$S/results/run3-library/`):

```
Error while indexing …/lunar/definitions/neovim-779c3696…/vim/_meta/vimfn.gen.lua
Caused by: java.io.UTFDataFormatException: encoded string (vim.fn.a...a	423698) too long: 78363 bytes
	at java.base/java.io.DataOutputStream.writeUTF(DataOutputStream.java:370)
	at net.internetisalie.lunar.lang.indexing.LuaDescriptionIndex$StringDataExternalizer.save(LuaDescriptionIndex.kt:69)
```

In a test the error fails the test (`TestLoggerAssertionError`). In the IDE it is an IDE error report
for every user who enables the library.

## 2. Expected vs actual

- **Expected:** a file whose description tokens are shared by many declarations indexes without error.
- **Actual:** an index value over 65535 encoded bytes throws, and the file's indexing fails.

## 3. Root cause

`DataOutput.writeUTF`'s 16-bit length prefix (in `StringDataExternalizer.save`, read back by
`readUTF` in `read`). One value holds every record for one token in one file, so its size grows with
the number of documented declarations sharing a word, without bound. A value of tens of kilobytes per
key is itself wasteful; bounding it (for instance, dropping stop-words such as `the`) is a separate
design question, not this fix.

The other `FileBasedIndex` externalizers in `lang/indexing/` that call `writeUTF`
(`LuaGlobalAssignmentIndex`, `LuaCatsTypeNameIndex`, `LuaMemberFieldIndex`) store `""` as their
value, and `LuaReceiverMemberIndex` / `LuaFileBindingsIndex` write one name per call, so none of them
concatenates records into one value the way this index does.

## 4. Fix strategy

- `StringDataExternalizer.save`: `IOUtil.writeUTF(output, value)`; `read`:
  `IOUtil.readUTF(input)` (`com.intellij.util.io.IOUtil`), which has no 64 KB cap (it falls back to
  a length-prefixed char sequence past the `writeUTF` limit).
- `getVersion()` 4 → 5, with a comment line in the existing style naming BUG-503. The bump is part of
  the fix: without it a persisted index fails to read (`An exception during updateWithMap(). Index
  lunar.luacats.descriptions will be rebuilt`, caused by `java.io.EOFException`; `$S/run4-library.log`).
  With both: `BUILD SUCCESSFUL` (`$S/run5-library.log`).

## 5. Test strategy

New class `net.internetisalie.lunar.lang.indexing.LuaDescriptionIndexValueSizeTest :
BasePlatformTestCase`, not a case in `LuaDescriptionIndexTest` (CI skips that class under
`-PexcludeExternalFixtureTests`). Case `testAValueOver64KbRoundTrips`: `myFixture.addFileToProject("big.lua", text)` where `text` is 3000
repetitions of `--- Returns the value.\nfunction vim.fn.f<i>() end\n` (`i` = 1…3000); then, in a read
action, `FileBasedIndex.getInstance().getValues(LuaDescriptionIndex.KEY, "returns",
GlobalSearchScope.allScope(project))`, flattened through `DescriptionRecord.parseAll` and filtered to
records whose `fileUrl` ends `/big.lua`. Expected: exactly 3000 records, owner names
`vim.fn.f1` … `vim.fn.f3000` in order. Other files in the light project (bundled stdlib stubs) also
carry `returns`, hence the filter.

## Resolution

Fixed in `LuaDescriptionIndex.StringDataExternalizer` and `LuaDescriptionIndex.getVersion` (4 → 5) as
§4 describes. Regression test:
`src/test/kotlin/net/internetisalie/lunar/lang/indexing/LuaDescriptionIndexValueSizeTest.kt`.

Measured on the libvirt builder, `test --rerun --no-build-cache` on parent `cb05af0f` plus this
change; logs and JUnit XML under `~/.cache/claude-scratch/lunar/bug-503/`.

| Run | Tree | Result |
| :-- | :-- | :-- |
| `r1-red` | test only, fix absent | fails: `TestLoggerAssertionError: encoded string (vim.fn.f...a	151865) too long: 116684 bytes`, thrown from `DataOutputStream.writeUTF` in `StringDataExternalizer.save` |
| `r2-green` | fix applied | passes (1 test, 0 failures) |
| `m1-save-reverted` | `save` back to `output.writeUTF` | fails: the `r1-red` message |
| `m2-read-reverted` | `read` back to `input.readUTF()` | fails: `malformed input around byte 20` |
| `r3-existing` | fix applied | `LuaDescriptionIndexTest`, `LuaDescriptionRecordTest`, `LuaDocSearchEverywhereContributorTest` and the new test: 21 tests, 0 failures |
| `r5-full` | fix applied, full suite | `BUILD SUCCESSFUL`; JUnit XML: 498 files, 3176 tests, 0 failures, 0 errors, 4 skipped |

Not covered by a test: the `getVersion()` bump. A light fixture builds its index fresh, so no unit
test can observe a persisted index written in the old format; the evidence that the bump is required
is TARGET-11's `$S/run4-library.log` (`EOFException` without it).

The corpus sweep was not run: the change alters only how an index value is serialised, and a value
that round-trips today round-trips identically, so no inspection can see a different index.
