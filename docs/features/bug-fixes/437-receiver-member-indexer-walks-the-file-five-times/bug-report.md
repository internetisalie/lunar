---
id: "BUG-437"
title: "`LuaReceiverMemberIndex.Indexer.map` walks the file five times where one walk would do"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-437: the receiver-member indexer walks the file five times

*Source: COMP-09 Phase 5, [[COMP-09-00-DR-25]] — measured and deferred, not assumed. Full evidence in
[COMP-09 design §1.11.4](../../completion/09-member-enumeration/design.md).*

## 1. What it is

`LuaReceiverMemberIndex.Indexer.map` implements design §4.3's five member sources as five separate
traversals of the same PSI:

| Source | Traversal |
| :-- | :-- |
| `indexFunctionDeclarations` | `findChildrenOfType(LuaFuncDecl)` |
| `indexMemberAssignments` | `findChildrenOfType(LuaAssignmentStatement)` |
| `indexTableLiteralFields` | `findChildrenOfType(LuaAssignmentStatement)` — via `forEachBareBinding` → `forEachAssignedTarget` |
| `indexOpaqueBindings` | `findChildrenOfType(LuaAssignmentStatement)` — same again |
| `indexClassFields` | `findChildrenOfType(LuaCatsClassTag)` |

Five call sites over **three** element types, three of them the *identical* `LuaAssignmentStatement`
walk. (DR-25's original row said "four passes"; the count is five.)

## 2. What it costs — measured

On a 126 KiB / 3 600-member library file, medians of five, timed **after** the file's AST expansion
so the figures are the traversals and not the parse:

```
pass funcDecl    median = 12 946 us
pass assignment  median = 10 767 us
pass catsClass   median = 10 509 us
one shared walk  median = 10 016 us      <- PsiTreeUtil.processElements dispatching on all three
```

One shared walk costs the same as one `findChildrenOfType`. Four of the five walks are therefore
redundant at ~10 ms each — about **40 ms of the index's 67 ms per-file cost**, roughly 60 %.

For scale, on the same input and with the AST expansion out of the comparison,
`LuaReceiverMemberIndex` is **67 ms**, `LuaMemberFieldIndex` **20 ms**, `LuaGlobalAssignmentIndex`
**6 ms** — reproducing COMP-09 DR-18's warm 61 / 20 / 6.

## 3. Why it was deferred rather than done in COMP-09

- The cost is **one-off and persisted** (index build), on no completion latency path.
- It edits a **shipped index**: any divergence in what the merged walk records changes index content,
  which needs a `getVersion()` bump and the corpus gate.

## 4. The trap to avoid when re-measuring

**The first `findChildrenOfType` caller to touch the tree pays the file's whole AST expansion**
(~200–280 ms on this fixture), because `FileContentImpl` defers chameleon expansion past
`getPsiFile()` to first tree access. Timing `map` first therefore reports ~256 ms and timing it third
reports ~67 ms, for identical code. Force a traversal before the timed region, or the measurement
describes the parse.

## 5. Expected outcome

One `PsiTreeUtil.processElements` walk dispatching on `LuaFuncDecl` / `LuaAssignmentStatement` /
`LuaCatsClassTag`, feeding the same five recorders. Gate: the index's emitted map must be
**byte-identical** to today's on the COMP-09 fixtures and the corpus sweep, plus a `getVersion()`
bump if it is not.
