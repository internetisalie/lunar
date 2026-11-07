---
id: "TYPE-SURVEY-CAUSES"
title: "Type System Survey: Causes"
type: "guide"
folders:
  - "[[features/type/requirements|requirements]]"
---

# Type System Survey: Causes

The [change survey](type-system-change-survey.md) is its companion. Together they led to [[MAINT-51]],
[[MAINT-52]], [[MAINT-53]] and [[MAINT-54]]. Both are one reader's judgement, and nothing was executed.

# What actually causes bugs in Lunar's type system

Source: `docs/features/bug-fixes/*/bug-report.md` at `8a10c1e9` (203 reports), read as extracted
root-cause, fix and outcome sections, not every line. Code read: `lang/psi/types/`, `luacats.bnf`,
`luacats.flex`, `LuaCatsDeclarations.kt`, `LuaNameReference.kt`. Nothing was executed.

## Pass 1: rows from the evidence

Written before testing any hypothesis.

Grade: **proven** = executed probe or landed fix; **read** = from code, not run; **unknown**.
Status: the front-matter value; where a report has none, the status stated in its own text.

Cluster codes (defined after the table):
`P` LuaCATS parser gap, `X` annotation misread or misapplied, `S` declared type degraded on a route,
`M` representation lacks a distinction, `V` verdict from unmodelled semantics, `F` flow-insensitive,
`N` feature not built, `R` name or member resolution and indexes, `C` engine infrastructure,
`T` stub data, `O` plain one-off, `U` cause unknown.

| ID | Status | Grade | Cl | Root cause |
| :-- | :-- | :-- | :-- | :-- |
| BUG-8 | done | proven | M | Neither `LuaGraphType.Function` nor `LuaFunctionType` records that the receiver is absent from `params`; `checkFunctionCompatibility` pairs by raw position |
| BUG-9 | done | proven | X | `LuaPsiImplUtil.getCatsComment` skipped whitespace regardless of blank lines, so a detached `@class` attached to the next local |
| BUG-10 | done | proven | M | Same defect as BUG-8, with the method arriving as a value (no production change) |
| BUG-11 | done | proven | S | `funcTypeFromStub` made a raw-text `LuaTypeReference` so composite types resolved UNKNOWN; BUG-566's `declaredParamNodeFor` then built `Undefined\|nil`, which `simplify` reduced to `nil` |
| BUG-14 | done | proven | O | `LuaTypeGraph.minArity` added BUG-8's receiver shift unconditionally |
| BUG-15 | done | proven | M | Bare `function` mapped to `Function(emptyList(), …)`, indistinguishable from a real zero-parameter signature (`fromLuaType`, `TYPEOF_MAP`) |
| BUG-16 | done | proven | M | Unresolved union member became `Undefined`; `LuaTypeAlgebra.simplify` dropped it, leaving a stricter union than written (fix: map to `Any` in `fromLuaType`) |
| BUG-19 | done | proven | S | `demandDeclaredParams`/`carriesGraphNodes` skipped any function or table demand, because a structural check wires edges into a seed shared by every call |
| BUG-21 | todo | read | F | One `VariableElement` per variable; `visitAssignmentStatement` adds each write to it and `checkTypes` crosses `upSet × downSet` with no ordering, so a later write reaches an earlier demand |
| BUG-353 | partly fixed, rest closed via BUG-359 | proven | R | Member segments resolved by short name, not receiver-qualified name (fixed by NAV-12, `LuaMemberFieldIndex`); doc provider grabbed same-short-name symbols |
| BUG-354 | done | proven | P | Lexer merged a comment as LuaCATS without checking every line starts `---` (`fe91301f`) |
| BUG-355 | done | proven | P | Grammar did not tolerate `@`-prefixed description after a type (`a593e4c5`) |
| BUG-356 | done | proven | O | `LuaTypeGraph` anchored the error on the value element, not the use site, so the diagnostic was missed (`294f9fd3`) |
| BUG-357 | cancelled | proven | O | No defect: cannot reproduce; already fixed by BUG-133 |
| BUG-359 | done | proven | V | Un-noded RHS read fell into `visitBinOpExpr`'s `graph.nil` fallback, so a manufactured Nil met the concat's String use; closed by BUG-397 |
| BUG-389 | done | proven | R | `LuaRequireReferenceContributor` handled only `LuaTerminalExpr`, not the `LuaArgs` STRING of `require "x"` |
| BUG-390 | done | proven | C | `resolveWrite`'s `visited` guard was dropped at each `LazyValueElement` hop, so cycles overflowed the stack |
| BUG-391 | done | proven | R | `LuaNameReference` phase 2 searched only library and required files, though a global is visible everywhere |
| BUG-393 | done | proven | P | `description` refused `CODE`; `@param[opt]` had no rule; fix added `ldocParamTag` fallback |
| BUG-397 | done | proven | N | `visitNameRef` and `visitIndexExpr` deliberately left free globals untyped for the engine; three-phase fix seeded declared types |
| BUG-406 | done | proven | P | `paramTag` accepts any bare NAME as a type, so LDoc prose was read as a type; widened by BUG-393's silent fallback |
| BUG-413 | todo | read | O | `LuaDocGenerator` substring `when` chain; no type-engine involvement |
| BUG-416 | done | proven | V | Each reaching definition was checked alone, so an optional nil was reported as certain; exposed by BUG-397; unmodelled fallbacks also manufactured `Nil` |
| BUG-417 | done | proven | C | `fromLuaType` anchors synthetic nodes at the file; a failed check gave a file-wide ERROR that hid lower-severity results |
| BUG-419 | done | proven | V | Inferred demands were checked like contracts; fixed by `declaredDemand` and the ERROR/HYPOTHESIS split (corpus 2 168 → 907) |
| BUG-420 | todo | read | X | `LuaCatsDeclarations.parentTypeNames` returns `Base<string, number>` whole; `materializeClass` makes a `LuaTypeReference` of it, and the index key is `Base` (symptom measured) |
| BUG-423 | done | proven | V | `visitBinOpExpr` demanded exact `Number`/`String`, ignoring Lua coercion; fixed with demand-only `Trait`s |
| BUG-424 | done | proven | N | Operator metamethods unmodelled; `Table.metamethods` plus `implementsOperator` added |
| BUG-425 | done | proven | S | BUG-397's early return in `visitFuncCall` dropped declared parameter demands together with the callee demand |
| BUG-426 | done | proven | V | Polarity: `V.__index = V` records a demand, and `handleSetMetatable` read only `write` (fix: `mergedTableOf`, `upSet` lookup) |
| BUG-427 | done | proven | S | `LuaGlobalAssignmentIndex` omitted top-level `LuaFuncDecl`; a function expression owned no comment; declaration precedence undefined |
| BUG-428 | done | proven | V | `nil` arms of `and`/`or` reported as certain; fixed as a side effect of BUG-441's unknown-provenance gate, 2 residual sites are call-site-blind |
| BUG-429 | superseded | proven | N | Member enumeration materialized the whole type; absorbed by COMP-09's receiver-member index |
| BUG-430 | done | proven | R | `visitIndexExpr` anchors every suffix on the bare receiver; index `dottedTarget` and the completion arm disagreed |
| BUG-432 | done | proven | C | `resolveType` had no dumb-mode guard and `logError` reported `IndexNotReadyException` |
| BUG-434 | done | proven | C | `LuaTypeReference`'s memoized answer did not replay its recording frame |
| BUG-435 | done | proven | O | `injectNarrowedBinding` took `guard.narrowedType` wholesale; fix `narrowType` keeps an already-narrower original |
| BUG-436 | done | proven | R | Five indexers filtered `extension == "lua"` instead of the registered file type |
| BUG-437 | done | proven | C | Receiver-member indexer walked the file seven times |
| BUG-438 | done | proven | C | `LuaImplicitFields.collect` forced the AST via `findChildrenOfType` |
| BUG-439 | done | proven | R | Global member door consulted only the first assigning file; cross-file extenders needed a `LOCAL_BINDING` sentinel |
| BUG-440 | done | proven | N | No documentation target existed for a `---@field` member |
| BUG-441 | done | proven | M | `collectRhsNodes` dropped node-less expressions (`upSet` empty) and `checkTypes` tested each definition alone; fix adds an `Any` value and a provenance gate |
| BUG-443 | todo | unknown | U | Never reproduced; suspected library registration path |
| BUG-466 | done | proven | R | Dotted function and field assignment are two declarations in two indexes, so the lookup's count ambiguity vanished call sites |
| BUG-470 | done | proven | R | Same as BUG-472 (a pointer report) |
| BUG-472 | done | proven | R | `LuaBlockExt`/`scopeCrawlUp` exclude a declaration from its own scope, so its name resolved outward |
| BUG-473 | done | proven | C | `VariableElement.resolveRead` re-walked the closure; bidirectional `checkTableCompatibility` makes per-call-site demands a clique (remainder went to TYPE-12) |
| BUG-476 | done | proven | R | `LuaScopeProcessor`'s `is LuaFuncDecl` arm matched a dotted head by text and stopped the walk |
| BUG-481 | todo | read | V | `LuaTypeGraph.isCompatible` has no `Table` value against `Array` demand arm; report says the representation question is open |
| BUG-490 | todo | read | R | The last segment of a function name still reaches global lookup; `lexicalTargetOf` excludes only the lexical half (BUG-476) |
| BUG-498 | todo | read | C | `materializeClass` iterates `decls` in index order with last-write-wins |
| BUG-500 | todo | read | R | `LuaModuleFileResolver.resolveModuleCandidates`: `!moduleName.contains('.')` disjunct accepts every `init.lua` |
| BUG-501 | done | proven | R | `LuaReceiverMemberIndex.membersIn` is first-wins by name across files in hash order; `addMethodsOf` trusted that entry for `M` |
| BUG-502 | done | read | P | `tableLiteralEntry` has no `?`; `paramTag` fails and silently falls back to `ldocParamTag` (same as BUG-542) |
| BUG-503 | done | proven | C | `DataOutput.writeUTF` 64 KB cap in `LuaDescriptionIndex` |
| BUG-504 | done | proven | R | `LuaDeprecatedApiInspection.getDeprecatedTag` trusted a root name's resolve to `function root.x`, without asking which segment |
| BUG-505 | todo | proven | R | `LuaReceiverMemberIndex.Indexer.split` drops names with two separators (probe patch) |
| BUG-507 | todo | unknown | U | Not investigated by the report; not traced here |
| BUG-508 | todo | read | R | `LuaNameReference.getQualifiedName` builds a key from receiver text, so `M.clear` matches any file's `M`; BUG-509 ruled out as the cause |
| BUG-509 | done | proven | R | `LuaFuncStubElementType.indexStub` sank a name and dotted root without asking whether the root was local |
| BUG-510 | done | proven | R | Bare-name phase of `doMultiResolve` consulted class and alias indexes, whose hosts are always locals |
| BUG-511 | todo | read | R | `GlobalSymbolRankingService.collectClassSymbols` treats class-index hosts as global values |
| BUG-512 | todo | read | R | `funcDeclNameLeaves` records method leaves as bindings; `getQualifiedName` returns null for non-dotted chains and falls through to bare lookup |
| BUG-513 | done | proven | T | `lua-5.1` stubs did not match the 5.1 API |
| BUG-514 | done | proven | P | `<TAG_RETURN>` no string rule; `<TAG_TYPE>` no `...`; `argumentName` excludes it (report's section is read; landed) |
| BUG-516 | todo | read | R | References resolve by receiver text (`io.stdout:close`), the method is `File:close` |
| BUG-518 | todo | read | X | `getDeprecatedTag` needs a `LuaCatsCommentOwner`; `LuaAssignmentStatement` is not one |
| BUG-519 | todo | read | C | `isPinnable` needs empty `rescuedGlobals`; `doResolveGlobal` counts a stdlib global as rescued because stubs are outside project scope |
| BUG-520 | todo | read | R | Provider passes `lastParent = file`, hiding every statement; `processDeclarations` never offers `LuaFuncDecl` |
| BUG-531 | done | proven | C | Stub types created after serializer init (`LuaStubElementTypes` not held via `stubElementTypeHolder`) |
| BUG-541 | todo | unknown | U | Count flickers; candidate only, per the report |
| BUG-542 | done | proven | P | `tableLiteralEntry` and `functionSignatureArgument` have no `?` slot |
| BUG-543 | done | proven | P | `<TAG_FIELD>` no `{NUMBER}`; only `dictionaryType` accepted a bracket key |
| BUG-544 | done | proven | P | No optional suffix in type grammar; `?` "worked" by falling into `description` |
| BUG-545 | done | proven | P | `functionSignatureReturnType` accepts one `type` only |
| BUG-546 | done | proven | P | `functionSignatureType` has no `async` prefix |
| BUG-547 | done | proven | P | `functionSignatureArgument` has only the named form |
| BUG-548 | done | proven | P | `<TAG_FIELD>` has no string rule |
| BUG-549 | done | proven | P | `seeTag` requires NAME; `<TAG_SEE>` has no `\|` |
| BUG-550 | done | proven | R | Four private copies of "is this a require call"; chained-call args misrecognised |
| BUG-551 | done | proven | T | `loadstring` carried `---@deprecated` in stubs |
| BUG-552 | done | proven | S | `?` survives as `Parameter.isOptional` but `demandDeclaredParams` used the bare type (fix: `demandTypeOf`) |
| BUG-553 | done | proven | V | Colon or dot access demands a one-member `Table`; `resolveRead` merges table demands, and `typeOf` returns `read` when `write` is undefined, so a demand is presented as the type |
| BUG-554 | done | proven | S | `funcTypeFromStub` read params as a name→type map with no `?`; the stub schema could not carry it |
| BUG-555 | todo | read | X | Top-level `?` stays in `description`; `LuaCatsDeclarations.fieldMember` honours only `name?` |
| BUG-556 | todo | read | X | `fieldMember` names a keyed field by descriptor text, and `checkTableCompatibility` then requires member `"[string]"` |
| BUG-557 | todo | read | X | `visitAssignmentStatement` injects every `getAllCatsComments` result, and `injectTypeAnnotation` falls back from `@type` to `@class`, so each `@class` block types the target |
| BUG-558 | todo | read | X | `injectTypeAnnotation` treats `@class` like `@type`, adding a declared demand on the class's own table |
| BUG-559 | todo | read | P | `<TAG_PARAM>` has no string rule; `paramTag` fails into `ldocParamTag` |
| BUG-560 | todo | read | P | `type ::= '(' unionType ')' \| unionType`; no `[]` after the parenthesis |
| BUG-561 | todo | read | P | Stubs spell `'"nil"'`, `STRINGS` is `{APOS}{NAME}{APOS}`; `<TAG_PARAM>` lacks `fun(...)` handling |
| BUG-562 | todo | read | P | `<TAG_RETURN>` has no `{NUMBER}` rule |
| BUG-563 | todo | read | P | `<TAG_CAST>` has no `{ELLIPSIS}` rule |
| BUG-566 | done | proven | S | `memberNodeFor` dropped `declaredDemand`, so a method's declared `@param` was filed HYPOTHESIS, which inspections skip |

Rows: 95 of 203 reports.

### Clusters, bottom-up

| Cl | Category | Count | IDs | Open |
| :-- | :-- | --: | :-- | --: |
| R | Name and member resolution, indexes | 23 | 353 389 391 430 436 439 466 470 472 476 490 500 501 504 505 508 509 510 511 512 516 520 550 | 8 |
| P | LuaCATS parser gaps | 19 | 354 355 393 406 502 514 542–549 559–563 | 5 |
| C | Engine infrastructure: cache, performance, guards, platform | 11 | 390 417 432 434 437 438 473 498 503 519 531 | 2 |
| V | Verdict from unmodelled semantics, demand treated as fact | 8 | 359 416 419 423 426 428 481 553 | 1 |
| X | Annotation misread or applied to the wrong thing | 7 | 9 420 518 555 556 557 558 | 6 |
| S | Declared type degraded on its way to a check | 7 | 11 19 425 427 552 554 566 | 0 |
| M | Representation lacks a distinction | 5 | 8 10 15 16 441 | 0 |
| O | Plain one-off | 5 | 14 356 357 413 435 | 1 |
| N | Feature not built yet | 4 | 397 424 429 440 | 0 |
| U | Cause unknown | 3 | 443 507 541 | 3 |
| T | Stub data | 2 | 513 551 | 0 |
| F | Flow-insensitive variable | 1 | 21 | 1 |

Reading of the clusters:

- **The two largest clusters, 42 of 95, are outside the type graph.** R lives in `LuaNameReference`,
  scope processors and indexes. P lives in `luacats.bnf` and `luacats.flex`.
- **R has a sub-pattern: text standing in for identity.** Eleven rows (430 439 490 501 504 508 509
  510 511 512 516) use a name or receiver string where a binding is meant. `LuaNameReference.getQualifiedName`
  and `membersIn` are the mechanisms, and AGENTS.md records it as a known fact of the design.
- **P has a sub-pattern: per-tag lexer states plus a silent fallback.** Lexer gaps repeat state by
  state (`<TAG_FIELD>`, `<TAG_PARAM>`, `<TAG_RETURN>`, `<TAG_CAST>`: 514 543 548 559 562 563), and
  `description` and `ldocParamTag` turn a failed type into prose with no error (406 502 544 545 559
  560).
- **Only S, M, V and F are in the engine's semantics core: 21 of 95.** One of those 21 pairs, 8 and
  10, is a single defect.
- **Open rows: 27 of 95.** 19 are in R, P or X. Only 2 are in S, M, V or F (21 and 481).

### Fixes that reopened or caused other bugs

| Cause | Effect | Kind |
| :-- | :-- | :-- |
| BUG-8 fix | BUG-14 | direct regression |
| BUG-566 fix | BUG-11 (optional arm of `declaredParamNodeFor`) | direct regression, unmerged branch |
| BUG-397 fix | BUG-416 (1 801 hits), BUG-425 (early return) | caused |
| BUG-393 fix | BUG-406 widened: a visible error became a silent bad type | caused |
| BUG-510 review | BUG-511 | sibling, not caused |
| BUG-476 fix | BUG-490 | residual half |
| BUG-430 fix | BUG-505 | residual half (function form) |
| BUG-419, BUG-423 | BUG-423 errors became firm after BUG-419; BUG-424 is the same report's remainder | exposed |
| BUG-566 | BUG-8, 9, 10, 11 had to land first: a declared demand turned hidden hypotheses into errors | exposed |
| BUG-19 | BUG-21: its hits appeared when function demands began to be checked | exposed |
| LuaCATS batch 542–549 | BUG-554, 555, 556, 557, 558: newly parsed annotations reached the checker | exposed |
| BUG-554 | BUG-564, BUG-565: removed errors hid and caught other things | exposed |
| BUG-515 | BUG-541 | possibly exposed, not established |

Pattern: most of these are not regressions. Fixing an upstream layer (parser, declared demand) lets
more input reach a downstream layer that had never been exercised. Each checker widening so far has
found the next latent defect. Two rows are direct regressions from a fix's own logic (14, 11).

Fixes that needed more than one attempt: BUG-439 (3), BUG-441 (2), BUG-430 (first attempt reverted),
BUG-423 (first attempt reverted), BUG-419 (closed then reopened). These are all in S, M, V, F or the
receiver text area of R, which is where the code is hardest to change safely.

### Out of scope (108), one-word area

| Area | IDs |
| :-- | :-- |
| ui | 362 363 365 366 367 368 369 370 371 375 387 388 404 448 449 462 530 532 534 537 |
| debugger | 414 447 450 454 456 463 485 523 538 |
| rename | 457 465 467 468 469 471 475 479 480 |
| tests | 17 18 20 409 422 431 442 461 497 499 524 525 529 536 539 565 baseline-test-failures |
| corpus | 415 418 496 506 515 |
| rocks | 364 379 380 383 384 |
| provisioning | 372 373 533 535 |
| luacheck | 451 452 453 |
| formatter | 382 459 482 483 484 |
| lexer | 361 392 411 |
| parser | 517 |
| settings | 460 521 522 |
| test-runner | 526 527 |
| refactoring | 458 478 412 |
| lint | 421 445 |
| docker | 360 446 |
| other | 358 reformat, 374 filetype, 376 publish, 377 matrix, 378 chore, 381 redis, 385 scaffold, 386 annotator, 444 toolchain, 455 run, 464 docs, 474 perf-gate, 528 threading, 540 luacov, 564 inspection |

Coverage limits:

- BUG-132, 133, 135 and 349 are type-adjacent but have `requirements.md`, not `bug-report.md`, so
  they are not rows. Likewise BUG-134.
- Some type bugs have no report at all (BUG-398, 399, 400, 401, 402 are referenced in the roadmap,
  changelog or other reports). Rows understate early type bugs.
- The set is not a random sample. 22 rows (501, 542–562) name MAINT-39's lazy.nvim corpus or its
  grammar batch at the top of the report, and 7 more (503 504 505 507 508 509 510) come from
  TARGET-11's prototype. Rows follow where a new corpus was pointed. Several reports describe one
  defect (8/10, 470/472, 502/542, 544/545).
- False negatives are under-reported. The corpus ratchet is what finds bugs here, and it mostly sees
  false positives; BUG-439 records that subset defects are invisible to it.

## Pass 2: the three candidates against the rows

Test used: *fixing this cause alone would have prevented the report*. Where two causes were both
necessary (BUG-11), the report counts for each.

| Candidate | Truly caused | Share of 95 | Merely adjacent | Open rows caused |
| :-- | :-- | --: | :-- | --: |
| A. Declared types on shared mutable nodes | 1 (19), plus half of 425 | 1.1% | 3 (417 473 566) | 0 |
| B. Several routes for a declared signature | 6 (11 425 427 552 554 566) | 6.3% | 6 (8 14 15 19 397 420) | 0 |
| C. Undefined and Any | 2 (11 16) | 2.1% | 6 (359 397 416 419 428 441) | 0 |
| Any of the three | 8 (11 16 19 425 427 552 554 566) | 8.4% | | 0 |

**The three candidates are overfitted.** If the sample was the latest commits (BUG-15, 16, 19, an
inference from `git log`), two of its three bugs are the bulk of A's and C's counts. Together the
candidates account for 8 of 95 reports, and none of the 27 open ones. The pattern they describe is
real but small.

### A. Shared mutable nodes

- **For:** BUG-19 is exactly this. `carriesGraphNodes` documents it, and a real measurement backs it
  (`ExpectedCallbackResolverTest.testTableSortComparatorSlotResolves`). BUG-425's guard is the same.
- **Half of 425:** the bug's cause was the BUG-397 early return. The node-free guard only explains
  why class and callback parameters stayed unchecked afterwards.
- **Against, strongest:** BUG-19 was fixed by copying, 42 lines in `LuaTypesVisitor.kt`
  (`detachedDemand`). MAINT-25 had already removed the worse form, a shared mutable `Table`
  singleton, without a redesign. TYPE-14 plans per-call instantiation as a copy too (design §5.8).
- **Not settled:** `detachedDemand` still returns null for a `Table`, so a `---@param w wxWindow` on
  a declaration-typed callee remains unchecked. No report covers that, because a missing diagnostic
  does not move a false-positive ratchet. TYPE-14's generic classes (Risk 1.2, rated Medium) are the
  live test. This is the one place A may be understated.

### B. Several routes

- **For:** the strongest of the three. In-file methods and cross-file methods differed in BUG-554
  and BUG-566. BUG-552's optional rule exists twice (`demandTypeOf`, `declaredParamNodeFor`), and
  BUG-11 was a bug in the second copy. BUG-566 §4.2 shows a fix that had to be routed carefully.
- **Against, strongest:** every one of the 6 is closed, and none needed a graph change. BUG-8 is the
  counter-example to the thesis: its table shows all routes *agreeing*, on a model with no receiver.
  BUG-9 shows the opposite design working: one `getCatsComment` chokepoint, one fix, every reader
  corrected. MAINT-34 unified LuaCATS extraction the same way. BUG-554's fix had to change the stub
  schema, and a stub-backed route must exist in any design, because the platform needs signatures
  without parsing. So part of B is inherent, and the remedy is one shared function, not a new core.

### C. Undefined and Any

- **For:** BUG-16 and the second half of BUG-11 are direct. BUG-441's fix chose `Any` because
  `simplify` and `flatten` drop `Undefined`.
- **Against, strongest:** BUG-441's RC-2 is proven: *"every design aimed at the type algebra"* failed,
  because `checkTypes` tests each reaching definition alone and never consults the union. The
  algebra was not the cause. BUG-16's fix was 9 lines in `fromLuaType`.
- **What the rows actually show is wider than two spellings.** "Unknown" appeared in four forms: `Undefined`,
  `Any`, no node at all (BUG-441 RC-1) and a manufactured `Nil` (BUG-359, BUG-416's "no information
  is `Undefined`, never `Nil`"). `Undefined` and `Any` do mean different things (no information, and
  a declared gradual type), so merging them is not obviously right.

### What the three miss

Counts are of Pass 1 rows.

| Cause | Rows | Open | Where it lives |
| :-- | --: | --: | :-- |
| **Text standing in for identity** (a name or receiver string where a binding or type is meant) | 11 (430 439 490 501 504 508 509 510 511 512 516) | 5 | `LuaNameReference`, `LuaReceiverMemberIndex`, stub index sinks |
| **Per-tag lexer states and silent prose fallback** in LuaCATS | 11 (406 502 514 543 544 545 548 559 560 562 563) | 5 | `luacats.flex`, `luacats.bnf` |
| **Annotation semantics**: which tag means what, on what | 7 (cluster X) | 6 | `injectTypeAnnotation`, `LuaCatsDeclarations` |
| **Checker ahead of the model**: a verdict on Lua semantics not yet modelled | 8 (cluster V) | 1 | `checkTypes`, `isCompatible`, `resolveRead` |
| **Flow and call-site insensitivity** | 1 caused (21); adjacent 416 428 441 | 1 | single `VariableElement`; ANALYSIS-07 |

One process finding sits above all of these: **widening a layer exposes the next one.** The
exposure rows in Pass 1 show defects surfacing after an upstream fix or a new corpus: 8–11 behind
BUG-566, 554–558 behind the grammar batch, 21 behind BUG-19. That is latent defects being found by
new input, which a rewrite would reset rather than remove.

## Pass 3: verdict

**A redesign of the type engine's core is not justified by this evidence.**

"Core redesign" here means: declared types separate from inference nodes, one signature path,
explicit receiver, bare-function and gradual forms, and demands that are never read as types.

| Tier | Count | Share of 95 | IDs |
| :-- | --: | --: | :-- |
| Would likely have been prevented | 15 | 15.8% | 8 10 11 15 16 19 359 419 425 428 441 552 553 554 566 |
| Might have been, depends on design choices | 7 | 7.4% | 14 21 390 416 417 426 473 |
| Would not | 73 | 76.8% | the rest |

As distinct defects the first tier is 14, since 8 and 10 are one. So the range is **15 to 22 of 95,
16% to 23%**. All 15 are inside S, M, V and F, so a core redesign reaches 15 of those 21
rows, 71%. It is effective on the core's own bugs; those are a fifth of the whole set. The other six
are 21 and 416 (possible), 423, 426 and 481 (rule knowledge) and 427 (index and comment ownership).

**Why the rule-knowledge rows are not in the first tier:** 423 (coercion), 424 (metamethods),
426 (polarity) and 481 (constructors) are facts of Lua that any engine must be taught. A rewrite
starts from the same ignorance and rediscovers them against the same corpus.

**Open bugs it would fix: none for certain, 1 conditionally.**

- BUG-21, only if the new core is flow-sensitive, which is ANALYSIS-07's question, not this list's.
- BUG-481 is a missing `isCompatible` arm, and 556 and 558 are annotation semantics. All three are
  fixable in place.
- Of the first tier, all 15 are already closed. The redesign would pay for bugs that have been paid
  for, and 25 of the 27 open rows sit outside S, M, V and F (the 2 inside are 21 and 481).

**Targeted fixes will do.** In order of open rows cleared:

1. LuaCATS type lexing: one shared type state or rule set, and a visible signal when a typed tag
   falls back to prose. Clears 559–563 and stops this class (5 open).
2. Annotation semantics: `@class` is not `@type` in `injectTypeAnnotation`, a keyed field is an index
   signature, and top-level `T?` is honoured. Clears 555–558 (4 open).
3. Member resolution through the receiver's type, not its text: 508 512 516, and possibly 490 and
   511. This is the only place a structural change is warranted. It is in `LuaNameReference`, not
   the graph, and it has not been costed here.
4. Finish B: one function yielding a declared signature, replacing the two copies of the optional
   rule. Cheap, and it protects TYPE-14.

**What would change this verdict:**

- TYPE-14's per-call copy (Risk 1.2) failing or spreading, which would raise A.
- ANALYSIS-07's spike showing that value-flow needs a CFG-based core, which would raise F. Its own
  headroom table reads 0, 5, 5 and 0 assignability hits on the four unannotated corpora, so the
  annotated members are where it must be measured.
- The [[MAINT-54]] false-negative audit returned **REOPEN** (`F` 0.79, `R` 0.95): a large silent class
  exists, mostly bundled-stdlib arity and `table` demands; see the [audit results](features/maint/54-type-false-negative-audit/audit-results.md).

**Uncertainty.**

- The prevention tiers are my counterfactual judgement, not measurement. A redesign could also
  ship new bugs, and two direct regressions here (14, 11) came from one-line-scale changes.
- Cluster assignment is one-per-row, and some rows span clusters (427, 441).
- The 24 read rows were not executed, including the open X and P causes, which I traced from code.
- 3 rows have no known cause. BUG-507 could add to the text-identity count if traced.
- Reports were read through root-cause, fix and outcome sections, not in full; BUG-473 (85 KB) and
  BUG-472 (45 KB) only in part.
- Scope is a judgement. Excluding name resolution, parser and stub rows would raise the 16–23% share,
  though not the zero open bugs it fixes for certain.
