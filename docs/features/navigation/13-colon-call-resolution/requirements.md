---
id: NAVIGATION-13
title: "13: Colon Call Site Resolution"
type: feature
parent_id: NAV
status: "todo"
priority: "high"
folders:
  - "[[features/navigation/requirements|requirements]]"
---

# NAV-13: a colon call site resolves to the method it calls

Completes [[NAV-12]]'s stated Non-Goal — *"Receiver type-narrowing resolution (resolving
`local p = package; p.path` via `p`'s inferred type)"* — for the colon form, and is the capability
[[REFACT-09]] is blocked on.

## Why this is filed now, with the measurement that forced it

`obj:m()` does not resolve. `LuaNameReference` keys on the receiver's **text**, so
`local b = Builder; b:setName()` never reaches `function Builder:setName` (`.agents/AGENTS.md`,
"Type engine" §3). The consequence is not merely a missing Go-to-Declaration:

- **`ReferencesSearch.search(<colon declaration leaf>, allScope)` returns 0 in every receiver
  shape**, the `---@class`-annotated one included — measured by `REFACT-09-00-DR-02`.
  `LuaNameReferenceSearcher` does scan colon call sites, but gates on `isReferenceTo`, which
  resolves; a colon call site resolves to nothing, so the gate never passes.
- With no usage set, [[REFACT-09]] had to prove a rename's completeness **syntactically**. Its
  predicate is sound — a Step 9 reviewer attacked it across eleven aliasing shapes without breaking
  it — and it **accepts 0 of 941** colon-method declarations across the 734-file corpus. 929 of
  those are refused by two or more clauses independently, so no single relaxation helps; deleting
  the whole escape set accepts 394 with no completeness evidence at all, which is
  `REFACT-01-00-DR-03`'s measured half-rename.

**So the missing capability is the reference direction, not the rename.** [[TYPE-13]] made a member
yield its declaration (`LuaMemberDeclarations.declarationOf`); what does not exist is a call site
that resolves *to* it.

## Scope

### In Scope
- `obj:m()` resolving to the `function Obj:m()` that declares it, for the receiver shapes TYPE-13
  measured as reaching a declaration: a plain local table, an in-file global table, and
  `setmetatable`-based OO through its supertype chain.
- `isReferenceTo` answering true for those sites, so `LuaNameReferenceSearcher`'s existing scan
  admits them and `ReferencesSearch` returns a usage set.
- Go to Declaration and Find Usages following from that, per NAV-01/NAV-02's existing routes.

### Out of Scope
- The rename itself — that is [[REFACT-09]], which this unblocks.
- Receiver shapes TYPE-13 measured as reporting **no** declaration: a `require`d module
  (TYPE-13 Gap 2.11), a chain's second segment (Gap 2.12), and the empty-`upSet` shapes —
  factory-returned tables, `self` receivers and nested constructors (Gap 2.7). Each is an engine
  merge change TYPE-13 design §8 puts out of scope.
- Widening `visitFuncCall` beyond `nameAndArgsList.firstOrNull()`.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `NAV-13-01` | **A colon call site resolves** | **M** | Not Implemented | `t:m()` resolves to `function t:m()` for the three shapes TYPE-13 reaches. |
| `NAV-13-02` | **`isReferenceTo` admits it** | **M** | Not Implemented | The declaration leaf and the call site agree, so `LuaNameReferenceSearcher`'s gate passes. |
| `NAV-13-03` | **`ReferencesSearch` returns the usage set** | **M** | Not Implemented | The measured 0 becomes the call sites that bind. This is the row [[REFACT-09]] consumes. |
| `NAV-13-04` | **An unreachable receiver resolves to nothing, not to something wrong** | **M** | Not Implemented | The Out-of-Scope shapes must return null rather than a plausible-but-wrong target. TYPE-13 Gap 2.12 measured the chain case returning a *silently wrong* value, which is the failure mode to avoid. |
| `NAV-13-05` | **No resolution regression** | **M** | Not Implemented | Existing dotted and qualified-name resolution is unchanged; NAV-01/02/12's gates hold. |
| `NAV-13-06` | **Cost** | **S** | Not Implemented | Resolution runs on every reference; BUG-473's budget gates must hold. |

## Open questions for planning

- **Where the resolution hangs.** `LuaNameReference` keys on text today. Whether this becomes a
  second reference type, a branch inside the existing one, or a `PsiReferenceContributor` is a
  design decision — note that `REFACT-08-00-DR-02` measured a `psi.referenceContributor` as
  **inert** for cats elements because they never consult `ReferenceProvidersRegistry`; whether the
  same holds for `LuaNameRef` must be executed, not assumed.
- **Cost.** Every reference resolution would consult the type engine. BUG-473 exists because that
  path was superlinear, and TYPE-12 records growth still at ×5.9 per doubling. **Measure before
  designing**, and treat a budget regression as disqualifying rather than tunable.
- **Reach.** REFACT-09 was planned to the bar twice and then measured to reach nothing. The reach
  question here — how many of the corpus's 941 colon-method declarations would gain a resolving
  call site — must be answered by a DR **before** the design, not after.
