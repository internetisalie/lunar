---
id: "MAINT-54-RESULTS"
title: "Audit Results"
type: "results"
parent_id: "MAINT-54"
folders:
  - "[[features/maint/54-type-false-negative-audit/requirements|requirements]]"
---

# MAINT-54: Audit Results

## Verdict

**REOPEN** — `F = 0.79` (threshold 0.25), `R = 0.95` (threshold 0.5), computed by design §3.6's rule,
unchanged, over every member and every callee path (risks-and-gaps DR-03, resolved).

Four in five declared-demand sites the audit could measure are silent when given a wrong value, weighted
by population, and nearly all of that silence sits in mechanism A or B cells. The weight is in bundled
stdlib calls: `LIBRARY` carries 91 % of `P` and 93 % of `S`. Its two largest silent classes are arity
(no *Too many/Too few arguments* against a stub function, [[BUG-38]]) and `table` parameters accepting a
function ([[BUG-40]]). Both are engine checks that do not depend on where the callee is declared, which
is why `LIBRARY` is in scope. The strata below show the verdict does not depend on `LIBRARY` for `F`.
Without it, `F` would be 0.62, but its mechanism share would be 0.45, under the `R` threshold, so the
`R` side does depend on it. That is a reading aid, not the verdict.

## Run

| | |
|---|---|
| Command | `tooling/gce-builder/gce-builder.sh run "test -PwithTypeAudit --rerun --no-build-cache --tests '*FalseNegativeAudit*'"` (through `builder-run libvirt`), then `python3 tooling/corpus/type_audit_summary.py build/type-audit` |
| Harness commit | `28dfb5a2` (MAINT-54 Phase 1); `src/main` as on `066057d5` |
| Builder | libvirt, 2026-11-07, `BUILD SUCCESSFUL in 14m 26s`; 5 tests, 0 failures, 0 errors |
| `CAP` | 4 |
| Repeatability | the five `<member>.json` payloads are byte-identical to the Phase 1 DR-02 run's |

```
[type-audit:luacheck] files=132 population=1399 sites=52 elapsedMs=56396
[type-audit:luarocks] files=159 population=3992 sites=72 elapsedMs=224305
[type-audit:penlight] files=56 population=655 sites=48 elapsedMs=28472
[type-audit:lazynvim] files=54 population=2572 sites=223 elapsedMs=390647
[type-audit:zerobrane] files=72 population=1881 sites=53 elapsedMs=131869
```

## Strata

### By callee path
Pooled cell rates, so the path rows' `S` and `P` sum to the pooled `S` and `P`. "Mechanism A/B S" is the
part of `S` in node-carrying or route-divergent cells, as classified on the pooled cells.

| path | non-lenient population | in F (P) | measured | reported | other-rule | silent | noisy | skipped | S | S/P (stratum F) | share of P | share of S | mechanism A/B S | R share |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| LIBRARY | 8418 | 8418 | 234 | 81 | 10 | 143 | 0 | 4 | 6828.65 | 0.81 | 0.91 | 0.93 | 6711.00 | 0.92 |
| SAME_FILE | 469 | 469 | 69 | 31 | 8 | 30 | 0 | 0 | 246.75 | 0.53 | 0.05 | 0.03 | 0.00 | 0.00 |
| PROJECT_FILE | 258 | 251 | 53 | 9 | 1 | 43 | 0 | 6 | 172.75 | 0.69 | 0.03 | 0.02 | 147.25 | 0.02 |
| CHECKOUT_FILE | 80 | 80 | 20 | 0 | 0 | 20 | 0 | 0 | 80.00 | 1.00 | 0.01 | 0.01 | 76.00 | 0.01 |
| **pooled** | | 9218 | | | | | | | 7328.15 | 0.79 | 1.00 | 1.00 | 6934.25 | 0.95 |

All paths except `LIBRARY`: `P` 800, `S` 499.50, `S/P` 0.62, mechanism A/B `S` 223.25 (share 0.45).

### By member
`verdict()` applied to one member's payload alone: rates and route divergence come from that member's
cells only, so these rows do not add up to the pooled row. Only lazynvim has callees outside `LIBRARY`.

| member | non-lenient population | in F (P) | measured | reported | other-rule | silent | F | R | rule's answer on this member alone | of which LIBRARY population |
|---|---|---|---|---|---|---|---|---|---|---|
| lazynvim | 2190 | 2182 | 181 | 53 | 10 | 118 | 0.77 | 0.83 | REOPEN | 1383 |
| luacheck | 1120 | 1120 | 44 | 20 | 0 | 24 | 0.92 | 0.27 | HOLD | 1120 |
| luarocks | 3785 | 3785 | 64 | 18 | 1 | 45 | 0.75 | 0.08 | HOLD | 3785 |
| penlight | 573 | 573 | 42 | 15 | 4 | 23 | 0.81 | 0.18 | HOLD | 573 |
| zerobrane | 1557 | 1557 | 45 | 15 | 4 | 26 | 0.87 | 0.24 | HOLD | 1557 |

### Where `LIBRARY` callees live
The payload does not record a `LIBRARY` callee's file, so this is a sample, not a count. All 276 `LIBRARY`
records were read back at their corpus line. On 275, a function the line calls is declared, by name, in the
bundled runtime stubs under `src/main/resources/runtime/standard/` or `runtime/luajit/`: `assert`,
`error`, `ipairs`, `pairs`, `require`, `setmetatable`, `type`, `table.*`, `os.*`, `io.open`, `math.*`,
`string.*`, `debug.*`, `coroutine.*`, `loadstring`, `setfenv`, `unpack`, `package.loadlib`, and the
`File:read`/`File:seek` methods. The remaining record is a multi-line call whose line holds only `end)`.
Definition libraries are disabled for every member, so no other library root is attached. The sample
matches names, not resolved targets.

### `arg.array`
Unmeasurable where the engine checks the argument: every table argument to an array type already errors
(DR-01e; tracked as [[BUG-481]], which now records the parameter route). Its 8 measured sites are all on
routes that check nothing, so neither `true` nor the `{}` control raises anything there. They are in `F` by
the unchanged rule, with population 10 of 9 218.

## Summary output (verbatim)

CAP = 4; members: lazynvim `85c7ff3711b730b4030d03144f6db6375044ae82`, luacheck `cc089e3f65acdd1ef8716cc73a3eca24a6b845e4`, luarocks `990ec6ca3b097c7160fe925cfca4b8e57cfe5685`, penlight `e0bc8f7fce3b6a4fdef3660066f5006bf8456b32`, zerobrane `483ef0dcb593f9599ff27715472425e39a0588e8`

### Shapes (pooled)

| shape | population | measured | reported | other-rule | silent | lenient | noisy | skipped | failed | reported, no AT_SITE | BODY entries | DECLARING_FILE entries | silent rate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| arg.array | 10 | 8 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.class | 100 | 32 | 8 | 1 | 23 | 0 | 0 | 0 | 0 | 0 | 92 | 0 | 0.72 |
| arg.function | 18 | 16 | 9 | 0 | 7 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0.44 |
| arg.optional | 365 | 62 | 39 | 2 | 21 | 0 | 0 | 0 | 0 | 1 | 3 | 0 | 0.34 |
| arg.primitive | 1455 | 60 | 33 | 8 | 19 | 0 | 0 | 0 | 0 | 0 | 5 | 0 | 0.32 |
| arg.record | 2 | 2 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.50 |
| arg.table | 1172 | 47 | 0 | 3 | 44 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.94 |
| arg.union | 89 | 14 | 9 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.36 |
| arity.tooFew | 2178 | 61 | 10 | 2 | 49 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0.80 |
| arity.tooMany | 3781 | 66 | 10 | 2 | 54 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0.82 |
| field.assign | 45 | 4 | 0 | 0 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 1.00 |
| lenient.primitiveForTable | 1274 | 0 | 0 | 0 | 0 | 62 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| return.mismatch | 10 | 4 | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 0.25 |

### Cells (pooled)

| shape | path | form | population | measured | reported | other-rule | silent | lenient | noisy | skipped | failed | reported, no AT_SITE | BODY entries | DECLARING_FILE entries | silent rate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| arg.array | CHECKOUT_FILE | MEMBER | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.array | PROJECT_FILE | MEMBER | 6 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.array | SAME_FILE | MEMBER | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.class | CHECKOUT_FILE | MEMBER | 9 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.class | LIBRARY | MEMBER | 9 | 8 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.class | PROJECT_FILE | MEMBER | 12 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.class | PROJECT_FILE | METHOD | 7 | 4 | 0 | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.75 |
| arg.class | SAME_FILE | LOCAL | 9 | 4 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| arg.class | SAME_FILE | MEMBER | 42 | 4 | 1 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 0.75 |
| arg.class | SAME_FILE | METHOD | 12 | 4 | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 83 | 0 | 0.25 |
| arg.function | LIBRARY | MEMBER | 10 | 9 | 9 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0.00 |
| arg.function | PROJECT_FILE | MEMBER | 5 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.function | PROJECT_FILE | METHOD | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.function | SAME_FILE | MEMBER | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.optional | CHECKOUT_FILE | MEMBER | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.optional | LIBRARY | GLOBAL | 165 | 20 | 20 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| arg.optional | LIBRARY | MEMBER | 146 | 20 | 14 | 2 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.20 |
| arg.optional | LIBRARY | METHOD | 19 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.optional | PROJECT_FILE | MEMBER | 6 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.optional | PROJECT_FILE | METHOD | 3 | 3 | 2 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.33 |
| arg.optional | SAME_FILE | LOCAL | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0.00 |
| arg.optional | SAME_FILE | MEMBER | 6 | 4 | 1 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0.75 |
| arg.optional | SAME_FILE | METHOD | 17 | 4 | 1 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0.75 |
| arg.primitive | CHECKOUT_FILE | MEMBER | 9 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.primitive | LIBRARY | GLOBAL | 786 | 20 | 17 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| arg.primitive | LIBRARY | MEMBER | 569 | 20 | 12 | 5 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.15 |
| arg.primitive | PROJECT_FILE | MEMBER | 21 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.primitive | PROJECT_FILE | METHOD | 22 | 4 | 1 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.75 |
| arg.primitive | SAME_FILE | MEMBER | 31 | 4 | 2 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 5 | 0 | 0.50 |
| arg.primitive | SAME_FILE | METHOD | 17 | 4 | 1 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.75 |
| arg.record | PROJECT_FILE | METHOD | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.record | SAME_FILE | MEMBER | 1 | 1 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| arg.table | LIBRARY | GLOBAL | 1017 | 20 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.table | LIBRARY | MEMBER | 146 | 18 | 0 | 0 | 18 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.table | PROJECT_FILE | MEMBER | 4 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.table | PROJECT_FILE | METHOD | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arg.table | SAME_FILE | METHOD | 3 | 3 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| arg.union | LIBRARY | MEMBER | 31 | 10 | 9 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.10 |
| arg.union | LIBRARY | METHOD | 58 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooFew | CHECKOUT_FILE | MEMBER | 19 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooFew | LIBRARY | GLOBAL | 1626 | 19 | 0 | 0 | 19 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooFew | LIBRARY | MEMBER | 395 | 19 | 0 | 0 | 19 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooFew | PROJECT_FILE | MEMBER | 28 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooFew | PROJECT_FILE | METHOD | 24 | 3 | 3 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0.00 |
| arity.tooFew | SAME_FILE | LOCAL | 5 | 4 | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| arity.tooFew | SAME_FILE | MEMBER | 56 | 4 | 1 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.75 |
| arity.tooFew | SAME_FILE | METHOD | 25 | 4 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| arity.tooMany | CHECKOUT_FILE | MEMBER | 39 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooMany | LIBRARY | GLOBAL | 2613 | 20 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooMany | LIBRARY | MEMBER | 751 | 19 | 0 | 0 | 19 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooMany | LIBRARY | METHOD | 77 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooMany | PROJECT_FILE | MEMBER | 64 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| arity.tooMany | PROJECT_FILE | METHOD | 45 | 3 | 3 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0.00 |
| arity.tooMany | SAME_FILE | LOCAL | 14 | 4 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| arity.tooMany | SAME_FILE | MEMBER | 107 | 4 | 1 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.75 |
| arity.tooMany | SAME_FILE | METHOD | 71 | 4 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.00 |
| field.assign | PROJECT_FILE | FIELD | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| field.assign | SAME_FILE | FIELD | 38 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1.00 |
| lenient.primitiveForTable | CHECKOUT_FILE | MEMBER | 9 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| lenient.primitiveForTable | LIBRARY | GLOBAL | 1017 | 0 | 0 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| lenient.primitiveForTable | LIBRARY | MEMBER | 155 | 0 | 0 | 0 | 0 | 18 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| lenient.primitiveForTable | PROJECT_FILE | MEMBER | 16 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| lenient.primitiveForTable | PROJECT_FILE | METHOD | 10 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| lenient.primitiveForTable | SAME_FILE | LOCAL | 9 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| lenient.primitiveForTable | SAME_FILE | MEMBER | 43 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| lenient.primitiveForTable | SAME_FILE | METHOD | 15 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| return.mismatch | SAME_FILE | MEMBER | 10 | 4 | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 0.25 |

### Coverage

| member | calls | stringOrTableArgs | unresolvedCallee | unresolvedMethod | formMismatch | noTag | arityGuard | eligible | fieldUntagged | ldocParam |
|---|---|---|---|---|---|---|---|---|---|---|
| lazynvim | 2732 | 0 | 638 | 343 | 4 | 412 | 448 | 887 | 78 | 0 |
| luacheck | 5551 | 374 | 1777 | 664 | 1 | 1975 | 279 | 481 | 0 | 0 |
| luarocks | 12313 | 117 | 5352 | 1583 | 3 | 2752 | 1034 | 1472 | 2 | 164 |
| penlight | 4498 | 330 | 2138 | 527 | 0 | 1111 | 85 | 307 | 0 | 380 |
| zerobrane | 9986 | 236 | 976 | 5726 | 1 | 2084 | 329 | 634 | 0 | 0 |
| pooled | 35080 | 1057 | 10881 | 8843 | 9 | 8334 | 2175 | 3781 | 80 | 544 |

### Verdict

- Unmeasured: unresolvedCallee 10881, unresolvedMethod 8843
- ldocParam 544: not read by the checker (not LuaCATS)
- Node-carrying cells: arg.class/CHECKOUT_FILE/MEMBER, arg.class/LIBRARY/MEMBER, arg.class/PROJECT_FILE/MEMBER, arg.class/PROJECT_FILE/METHOD, arg.table/LIBRARY/GLOBAL, arg.table/LIBRARY/MEMBER, arg.table/PROJECT_FILE/MEMBER, arg.table/PROJECT_FILE/METHOD
- Route-divergent cells: arg.class/CHECKOUT_FILE/MEMBER, arg.class/LIBRARY/MEMBER, arg.class/PROJECT_FILE/MEMBER, arg.function/PROJECT_FILE/MEMBER, arg.optional/LIBRARY/METHOD, arg.optional/PROJECT_FILE/MEMBER, arg.primitive/CHECKOUT_FILE/MEMBER, arg.primitive/PROJECT_FILE/MEMBER, arg.table/LIBRARY/GLOBAL, arg.table/LIBRARY/MEMBER, arg.table/PROJECT_FILE/MEMBER, arg.union/LIBRARY/METHOD, arity.tooFew/CHECKOUT_FILE/MEMBER, arity.tooFew/LIBRARY/GLOBAL, arity.tooFew/LIBRARY/MEMBER, arity.tooFew/PROJECT_FILE/MEMBER, arity.tooMany/CHECKOUT_FILE/MEMBER, arity.tooMany/LIBRARY/GLOBAL, arity.tooMany/LIBRARY/MEMBER, arity.tooMany/LIBRARY/METHOD, arity.tooMany/PROJECT_FILE/MEMBER
- S = 7328.15, P = 9218
- F = 0.79 (threshold 0.25), R = 0.95 (threshold 0.5)
- **Verdict: REOPEN**

## Cell → BUG

Every cell with `silent ≥ 1`. The grouping follows the route on which the silence occurs and a light-fixture
probe per group (in each BUG); root causes are left to `plan-bug`, which may merge or split them.

- [[BUG-38]] — arity is not checked against a function declared in another file (bundled stubs, other project files) (8 cells)
- [[BUG-40]] — a `table`-, class- or record-typed parameter declared in another file accepts a function (survey mechanism A) (9 cells)
- [[BUG-41]] — arguments to a function or method of a `require`d project module are not checked (10 cells)
- [[BUG-42]] — arguments to a function on a local table in the same file are not checked (partly) (10 cells)
- [[BUG-43]] — a method on a possibly-nil stdlib value (`io.open`'s file) is not checked (3 cells)
- [[BUG-44]] — assignment to a `@field` is a hypothesis only (1 cells)
- [[BUG-45]] — checked routes stay silent at a few sites, cause unknown (4 cells)

| Cell (shape / path / form) | population | measured | silent | BUG |
|---|---|---|---|---|
| arg.array / CHECKOUT_FILE / MEMBER | 2 | 2 | 2 | [[BUG-41]] |
| arg.array / PROJECT_FILE / MEMBER | 6 | 4 | 4 | [[BUG-41]] |
| arg.array / SAME_FILE / MEMBER | 2 | 2 | 2 | [[BUG-42]] |
| arg.class / CHECKOUT_FILE / MEMBER | 9 | 4 | 4 | [[BUG-40]] |
| arg.class / LIBRARY / MEMBER | 9 | 8 | 8 | [[BUG-40]] |
| arg.class / PROJECT_FILE / MEMBER | 12 | 4 | 4 | [[BUG-40]] |
| arg.class / PROJECT_FILE / METHOD | 7 | 4 | 3 | [[BUG-40]] |
| arg.class / SAME_FILE / MEMBER | 42 | 4 | 3 | [[BUG-42]] |
| arg.class / SAME_FILE / METHOD | 12 | 4 | 1 | [[BUG-42]] |
| arg.function / PROJECT_FILE / MEMBER | 5 | 4 | 4 | [[BUG-41]] |
| arg.function / PROJECT_FILE / METHOD | 1 | 1 | 1 | [[BUG-41]] |
| arg.function / SAME_FILE / MEMBER | 2 | 2 | 2 | [[BUG-42]] |
| arg.optional / CHECKOUT_FILE / MEMBER | 2 | 2 | 2 | [[BUG-41]] |
| arg.optional / LIBRARY / MEMBER | 146 | 20 | 4 | [[BUG-45]] |
| arg.optional / LIBRARY / METHOD | 19 | 4 | 4 | [[BUG-43]] |
| arg.optional / PROJECT_FILE / MEMBER | 6 | 4 | 4 | [[BUG-41]] |
| arg.optional / PROJECT_FILE / METHOD | 3 | 3 | 1 | [[BUG-41]] |
| arg.optional / SAME_FILE / MEMBER | 6 | 4 | 3 | [[BUG-42]] |
| arg.optional / SAME_FILE / METHOD | 17 | 4 | 3 | [[BUG-42]] |
| arg.primitive / CHECKOUT_FILE / MEMBER | 9 | 4 | 4 | [[BUG-41]] |
| arg.primitive / LIBRARY / MEMBER | 569 | 20 | 3 | [[BUG-45]] |
| arg.primitive / PROJECT_FILE / MEMBER | 21 | 4 | 4 | [[BUG-41]] |
| arg.primitive / PROJECT_FILE / METHOD | 22 | 4 | 3 | [[BUG-41]] |
| arg.primitive / SAME_FILE / MEMBER | 31 | 4 | 2 | [[BUG-42]] |
| arg.primitive / SAME_FILE / METHOD | 17 | 4 | 3 | [[BUG-42]] |
| arg.record / PROJECT_FILE / METHOD | 1 | 1 | 1 | [[BUG-40]] |
| arg.table / LIBRARY / GLOBAL | 1017 | 20 | 20 | [[BUG-40]] |
| arg.table / LIBRARY / MEMBER | 146 | 18 | 18 | [[BUG-40]] |
| arg.table / PROJECT_FILE / MEMBER | 4 | 4 | 4 | [[BUG-40]] |
| arg.table / PROJECT_FILE / METHOD | 2 | 2 | 2 | [[BUG-40]] |
| arg.union / LIBRARY / MEMBER | 31 | 10 | 1 | [[BUG-45]] |
| arg.union / LIBRARY / METHOD | 58 | 4 | 4 | [[BUG-43]] |
| arity.tooFew / CHECKOUT_FILE / MEMBER | 19 | 4 | 4 | [[BUG-38]] |
| arity.tooFew / LIBRARY / GLOBAL | 1626 | 19 | 19 | [[BUG-38]] |
| arity.tooFew / LIBRARY / MEMBER | 395 | 19 | 19 | [[BUG-38]] |
| arity.tooFew / PROJECT_FILE / MEMBER | 28 | 4 | 4 | [[BUG-38]] |
| arity.tooFew / SAME_FILE / MEMBER | 56 | 4 | 3 | [[BUG-42]] |
| arity.tooMany / CHECKOUT_FILE / MEMBER | 39 | 4 | 4 | [[BUG-38]] |
| arity.tooMany / LIBRARY / GLOBAL | 2613 | 20 | 20 | [[BUG-38]] |
| arity.tooMany / LIBRARY / MEMBER | 751 | 19 | 19 | [[BUG-38]] |
| arity.tooMany / LIBRARY / METHOD | 77 | 4 | 4 | [[BUG-43]] |
| arity.tooMany / PROJECT_FILE / MEMBER | 64 | 4 | 4 | [[BUG-38]] |
| arity.tooMany / SAME_FILE / MEMBER | 107 | 4 | 3 | [[BUG-42]] |
| field.assign / SAME_FILE / FIELD | 38 | 4 | 4 | [[BUG-44]] |
| return.mismatch / SAME_FILE / MEMBER | 10 | 4 | 1 | [[BUG-45]] |

Not read as false negatives: `lenient.primitiveForTable` (62 sites, lenient by design); unresolved calls
(`unresolvedCallee` 10 881, `unresolvedMethod` 8 843) are unmeasured; LDoc `-- @param` lines (544) are not
LuaCATS.
