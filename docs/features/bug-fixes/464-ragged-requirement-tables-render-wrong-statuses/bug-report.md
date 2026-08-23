---
id: "BUG-464"
title: "Requirement tables with a cell count that disagrees with their header render the wrong status — 36 rows across 11 files"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-464: a table row with more cells than its header silently drops the excess

Found 2026-08-23 while remediating [[REFACT-01]] Phase 2, which had produced eleven such rows in
its own requirements table. Those eleven are fixed; this report covers the rest of the corpus.

## 1. What happens

Per the GFM tables spec, a row with **more** cells than the header has the excess **dropped**, and a
row with fewer is padded. Neither is an error, and no linter in this repo checks it — `lint_docs.py`
validates front-matter, not table shape. So a row that looks correct in source renders with its
columns shifted or its tail missing.

The consequence is specific and bad: **these are requirement tables, so the cell that goes missing
is usually Status or Description.** A reader sees a status that belongs to a different column, or a
row asserting `Full` beside evidence text that says the opposite.

## 2. Confirmed instances

- **`docs/features/documentation/requirements.md:17-22`** — a **4-column** header (`| ID | Requirement | Priority | Description |`), with `DOC-01` at 4 cells but `DOC-02`, `DOC-03`, `DOC-04` and `DOC-05` at **5**, each having gained a `**Full**` status cell. Those four rows render their status where the description belongs.
- **`docs/features/inspections/09-language-level/requirements.md:48`** — an unescaped `|` inside a code span (`` `&` `|` `~` ``) splits the row and drops its tail. Code spans do not protect pipes in GFM; only `\|` does.
- **36 ragged rows across 11 files** in total, counted by comparing each row's unescaped-pipe count against its own header's, with code spans normalised first.

## 3. Why it matters here specifically

The [[REFACT-01]] instance rendered `REFACT-01-01` as **Full** next to the description *"No
`renamePsiElementProcessor`, no `handleElementRename` override — grep is empty"*. That is a
requirements table displaying a false status, which is the exact defect that left the feature
believed shipped for months ([[BUG-450]] §4 traces the same species). A document whose rendered
claim differs from its source is not a documentation nit; it is the failure mode this repo keeps
paying for.

## 4. Fix strategy

Two parts, and the second is the durable one:

1. **Repair the 36 rows.** Mechanical, but each needs a judgement about which cell was intended —
   `DOC-02`…`DOC-05` may want the 5-column header rather than 4-cell rows.
2. **Add a table-shape check to `scripts/lint_docs.py`.** Compare each row's cell count against its
   header's, after normalising code spans and `\|`. This is cheap and it is the only part that stops
   the next one. Without it the repair is a snapshot, not a fix.

## 5. Test strategy

The linter check is the test. Its mutation: take any currently-correct table, add a cell to one row,
and confirm the linter goes red — then confirm it stays green on `\|` inside a cell and on a pipe
inside a code span, which are the two false positives that would make the check unusable.

Note the trap this bug is itself an instance of: a check that only counted rows with *fewer* cells
than the header would pass the entire corpus, because every confirmed instance here has **more**.
