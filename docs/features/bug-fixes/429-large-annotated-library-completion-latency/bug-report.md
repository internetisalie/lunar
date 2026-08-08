---
id: "BUG-429"
title: "Completion takes seconds against a large annotated library tree"
type: "bug"
status: "todo"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-429: Completion takes seconds against a large annotated library tree

Found by TARGET-10's DR-04, which is the first thing to register a definition library of realistic
size. Every bundled `runtime/` stub is small — the whole `runtime/` tree is 424 KiB across 78 files,
so no existing file or tree approaches what a generated wxLua tree would be, and this has never been
exercised.

## Measured (`TargetTenDrSpikeTest`, gce-builder, 2026-08-07)

All figures are wall-clock for the **first** `completeBasic()` after the root is indexed. Indexing
itself is fast throughout (1.5–2.7 s) — the cost is in completion, not indexing.

| Fixture | Tree | First completion |
| :-- | :-- | --: |
| single file, 5 550 consts + 4 050 methods | 530 KiB, 1 file | **25 352 ms** (broad prefix) |
| same, narrow prefix (few candidates) | 530 KiB, 1 file | **18 429 ms** |
| same, class-member caret | 530 KiB, 1 file | **17 680 ms** |
| namespace members in root, class bodies split out | 230 KiB root + 15 files | **12 902 ms** (namespace constant) |
| …same tree, class-member caret | as above | 2 645 ms |
| constants only | 40 KiB | 297 ms |
| constants only | 80 KiB | 560 ms |
| constants only | 160 KiB | 260 ms |
| constants only | 320 KiB / 8 652 consts | 1 917 ms |

**Candidate count is not the driver.** A narrow prefix with a handful of matches costs 18 s against
the same file a broad prefix costs 25 s. Nor is indexing.

**Two hypotheses remain, and they are not separated yet.** The constants-only rows reach 320 KiB at
1.9 s, while the 230 KiB row costs 12.9 s — so either

1. **annotated function declarations are far more expensive than annotated assignments** (the 230 KiB
   root holds ~270 `---@return` + `function wx.f()` declarations that the constants-only rows do
   not), or
2. **the cost is whole-tree, not per-file** — the 230 KiB row registers ~700 KiB across 16 files,
   the constants-only rows a single file.

Distinguishing them is the first task: they imply opposite fixes. If (2), splitting files cannot
help and only emitting fewer symbols can.

## Why it matters beyond TARGET-10

Any fetched LuaCATS library of real size hits this. `love2d` is 97 KiB today, which is why nothing
has surfaced it; `openresty` and future addons will grow. TARGET-10 is the trigger, not the cause.

## Not a TARGET-10 design defect

TARGET-10's DR-06 established the layout constraint independently: namespace members must live in
the file declaring `---@class <ns>` (a sibling file resolves nothing, with or without re-declaring
the class), while class bodies resolve cross-file by flat type name. So the emitted shape is already
the minimal-root arrangement, and it still costs 12.9 s.

## Verification

- A test that registers a ~250 KiB annotated root and asserts first completion under a stated
  budget. It must be written to fail today.
- Separate hypotheses (1) and (2) first, with a fixture that varies one and holds the other.
