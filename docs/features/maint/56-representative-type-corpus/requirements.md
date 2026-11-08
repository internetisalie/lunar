---
id: "MAINT-56"
title: "56: Representative Type-Checking Corpus and Real-World Rule Frequencies"
type: "feature"
parent_id: "MAINT"
status: "planned"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-56: Representative Type-Checking Corpus and Real-World Rule Frequencies

## Overview

[[MAINT-54]]'s REOPEN verdict rests on five mature tool repositories, four of them unannotated, and 91 %
of its weight is in calls to bundled standard-library stubs (`audit-results.md`, "By callee path"). This
feature re-runs the MAINT-54 audit over a corpus stratified by **host API** — the application whose API
the code scripts (Neovim, World of Warcraft, LÖVE, …), or the interpreter for code that targets no host —
each member configured as a user would configure it (target and definition libraries). Each domain's
weight in the headline figures is its measured share of public Lua code, not anything about this corpus.
The output becomes per-rule weights: the rules are MAINT-54's shapes (`arg.table`, `arity.tooMany`, …),
and [[MAINT-55]]'s open question on weighting its rules by importance is to be answered from them. Open bugs are weighted indirectly, through the rule
or rules they violate; whether to redesign the type engine is read from the same run's usage-weighted
verdict and the per-domain verdicts beside it.

For each MAINT-54 shape and each domain it measures **exposure** (sites per 1 000 non-blank lines),
**catch rate** (1 − MAINT-54's silent rate) and, per error family, the **suspected false-positive rate**
(findings on released code per 1 000 lines). A rule's importance multiplies these by a **real-bug rate**,
which comes from bug-fix mining, [[MAINT-58]].

## Scope

### In Scope
- A domain list, `tooling/corpus/type-domains.json`: each host's signatures and their measured public
  usage, re-measurable by `type_corpus.py usage` (design §2.0).
- A second manifest, `tooling/corpus/type-corpus.json`, stratified by those domains (design §2.1).
- Fetching it with the existing fetcher (`--manifest`, and sparse checkout for one member).
- A representative audit run: MAINT-54's harness, per member, with its target pinned and its definition
  libraries enabled, plus the baseline error families per member.
- A summary script producing per-domain verdicts and the rule-weights file.
- A results document.

### Out of Scope
- **Bug-fix mining and the audit re-weighted by it:** [[MAINT-58]], which multiplies this feature's rule weights by a real-bug rate. risks-and-gaps DR-05 is
  its feasibility evidence.
- **Runtime-error proxy** (counts of Lua runtime-error messages in public issue trackers): Won't. The one-off snapshot is
  risks-and-gaps DR-06; its counts are dominated by end-user crash reports and cannot be mapped to rules.
- Fixing any bug; changing anything under `src/main`; changing `corpus.json` or any sweep baseline.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-56-01 | **Stratified manifest** | M | `type-corpus.json` lists the members of design §2.1; every entry has a `domain` from `type-domains.json`, a `cohort` and a `licence`; every sampled domain has ≥ 3 members; no name also appears in `corpus.json`. `type_corpus.py check` enforces this. |
| MAINT-56-02 | **Annotation density** | M | Per member and per domain, LuaCATS tag lines per 1 000 non-blank lines of the member's roots (design §3.1). The results document reports it beside every domain figure. |
| MAINT-56-03 | **Fetch** | M | `fetch-corpus.py --manifest tooling/corpus/type-corpus.json` fetches every member into `test/corpus/<name>`; an entry with `"sparse": true` fetches only its roots and `moduleRoot`. Without `--manifest` the fetcher's behaviour is unchanged. |
| MAINT-56-04 | **Representative audit run** | M | Under `-PwithTypeCorpus` only, each member is audited by MAINT-54's harness with the member's target pinned and its definition libraries enabled; it writes `build/type-corpus/audit/<member>.json` (MAINT-54 schema 1) and `build/type-corpus/extras/<member>.json` (design §4). MAINT-54's own run writes byte-identical payloads before and after the shared code moves. |
| MAINT-56-05 | **Exposure per rule** | M | Per shape and domain, sites per 1 000 lines (design §3.3). |
| MAINT-56-06 | **Catch rate and verdicts** | M | Per shape and domain, the silent rate; per domain, MAINT-54's `F`, `R` and verdict; the usage-weighted verdict (the headline), the unweighted domain-mean and pooled verdicts beside it, and the non-`LIBRARY` stratum (design §3.4). |
| MAINT-56-07 | **Suspected false positives** | M | Per error family and domain, reported errors on the unedited members per 1 000 lines (design §3.2, §3.5). |
| MAINT-56-08 | **Rule-weights file** | M | `type_corpus.py summarize` writes `tooling/corpus/rule-weights.json` in the schema of design §4.3 — the interface MAINT-55 and MAINT-58 read. |
| MAINT-56-09 | **Bug → rule mapping** | S | Each silent cell is mapped to an open BUG by design §3.6's rules, and `rule-weights.json` lists each mapped bug with the rules (shapes) of its cells. A bug no rule reaches carries no rule weight. |
| MAINT-56-10 | **Results document** | M | `results.md` here holds the run command and commits, the summary output verbatim, the density table, and the two decision readings of design §3.7. |
| MAINT-56-11 | **False-positive sample** | S | For each family with ≥ 1 finding, up to 10 findings (design §3.8 selection) are read and classified `real defect` / `false positive` / `unclear` in `results.md`. |
| MAINT-56-12 | **Host domains and usage weights** | M | `type-domains.json` holds the domains of design §2.0, each host's signatures and their GitHub code-search counts with the measurement date; `type_corpus.py usage` re-measures them. A weighted domain's weight is its share of the measured counts (design §2.0); nothing about the corpus's own members (count, size) enters a weight. |

## Behavior Rules
- The thresholds, formulas and bug → rule map are fixed before the run; they are not tuned to a result.
- A figure with no measured site is shown as `-`, never as 0.
- Every weighted figure is printed with the per-domain figures it was computed from.
- A domain whose usage cannot be measured (`"weighted": false`) is reported per domain and left out of
  every weighted figure; it is never given a weight by assumption.

## Test Cases

Python cases live in `tooling/corpus/test_type_corpus.py` (fixtures under
`tooling/corpus/testdata/type_corpus/`); Kotlin cases in `TypeAuditExtrasTest` and
`RepresentativeMemberSetTest`. Case numbers are stable identifiers, so the sequence has gaps. Each row names the mutation that must turn it red.

| # | Req | Given | When | Then | Mutation that turns it red |
|---|-----|-------|------|------|----------------------------|
| TC-1 | 01 | fixture domain list (sampled `love`); fixture manifest: `love` has 2 members, one entry, `zz`, lacks `cohort`, one is named `penlight` | `check(manifest, {"penlight"}, domains)` | errors, in order: `zz: missing 'cohort'`, `penlight: also in corpus.json`, `love: 2 members, need 3` | drop any one of the three checks |
| TC-2 | 01 | the real `type-corpus.json` and `corpus.json` | `main(["check"])` | exit 0, prints `type-corpus ok (<n> members, <k> domains)`, `<n>` = the manifest's entry count, `<k>` = the sampled domains in `type-domains.json` | add `lazynvim` to `type-corpus.json` |
| TC-3 | 02 | fixture checkout `dens/` with `a.lua` = `---@param x string`, `--- @return number`, `-- @param y`, blank, `local x = 1`; root `.` | `density(dens, ["."])` | `{"files": 1, "lines": 4, "catsTags": 2, "maxFileLines": 4, "maxFile": "a.lua"}` | tag regex without `\s*` after `---` (gives 1) |
| TC-4 | 03 | fixture manifest with one entry lacking `url`; `CORPUS_ROOT` patched to a temp dir | `fetch-corpus.main(["--manifest", fixture])` | `SystemExit(1)`, stderr names `Malformed manifest entry bad: missing 'url'` | `main` ignores `--manifest` and reads `corpus.json` |
| TC-5 | 03 | entry `{"roots": ["a/b"], "moduleRoot": "c"}` | `sparse_patterns(entry)` | `["/a/b/", "/c/"]` | omit `moduleRoot` |
| TC-6 | 04 | `type-corpus.json`; `LuaRepresentativeTypeAuditTest`'s `@Test` methods | `RepresentativeMemberSetTest` | `test<Name>Member` names == manifest names, each first letter upper-cased | delete `testLdeMember` |
| TC-7 | 04 | `AuditPayload(1, "m", "c", 4, AuditCoverage(), emptyList(), emptyList())`, a temp dir `tmp/out` | `AuditOutput.writeTo(tmp/out, "m", payload)` | `tmp/out/m.json` exists and its text == `AuditOutput.render(payload)` | `writeTo` writing to `directory.parentFile` |
| TC-8 | 04 | MAINT-54 payloads from a run on the base commit, saved | the same run after Phase 2 | `diff -r` of `build/type-audit` empty | change `AuditSelection.CAP` |
| TC-9 | 08 | `build/type-corpus/` missing `extras/lde.json` | `summarize` | exit 2, stderr `missing payload: extras/lde.json` | completeness check removed |
| TC-10 | 05 | testdata payloads: member `a` (domain `x`, 2 000 lines) has `arg.table` populations 1 + 1 in two cells; member `b` (domain `x`, 1 000 lines) has one cell of 2 | `exposure(...)["arg.table"]["x"]` | `(2/2 + 2/1) / 2 = 1.50` | lines pooled across members (`4/3 = 1.33`) |
| TC-11 | 06, 12 | domain `x` cells give `F` 0.50 with `P` 1 000, weight 0.75; domain `y` give `F` 0.10 with `P` 10, weight 0.25 | `domain_verdicts(...)` | `F_w = 0.40`; `F_dm = 0.30`; pooled `F` 0.50 | `F_w` as the unweighted mean (0.30) |
| TC-12 | 06 | domain `x` has a `LIBRARY` cell (rate 1.0, pop 100) and a `SAME_FILE` cell (rate 0.0, pop 100) | non-`LIBRARY` stratum | `F = 0.00` | stratum keeps `LIBRARY` cells (`F = 0.50`) |
| TC-13 | 07 | Lua: `---@param n number` / `local function f(n) end` / `f(1, 2)` / `f("x")` | `TypeAuditExtras.baselineFamilies(file)` | `{arity.tooMany=1, assign.other=2}` | classify by inspection rule (`LuaReturnTypeMismatch` → `return.mismatch`, gives `return.mismatch=1`; risks-and-gaps DR-07) |
| TC-16 | 09 | every row of MAINT-54 `audit-results.md` "Cell → BUG" as `(shape, path, form, bug)` | `bug_for_cell(cell, rules)`, `rules` = `tooling/corpus/type_bug_map.json` loaded | that row's bug; the `arity.*` rows marked BUG-38 (done) → `unmapped` | delete the BUG-43 rule (`arg.optional/LIBRARY/METHOD` → `unmapped`) |
| TC-19 | 12 | fixture domains: `a` sampled and weighted, host `h1` counts `{s1: 100, s2: 300}`, host `h2` `{s3: 100}`; `b` sampled and weighted `{s4: 600}`; `c` sampled, unweighted `{s5: 50}`; `d` unsampled `{s6: 1000}` | `weights_of(domains)` | `{"a": 0.40, "b": 0.60}`; `c` and `d` absent | a host's count as the sum of its signatures (`a` = 0.4545) |
| TC-20 | 12 | fixture domains with signature `vim.api`; a fake `search` returning 7 for `"vim.api" extension:lua` and 0 otherwise | `measure_usage(domains, search, "2026-11-08")` | counts `{"vim.api": 7}`, `measuredAt` `2026-11-08` | query built with `language:Lua` (gives 0) |
| TC-21 | 04 | fixture manifest entry `big` whose checkout holds one file of 3 501 non-blank lines | `check(..., checkouts=fixture)` | error `big: a.lua has 3501 lines, cap 3500` | the cap check removed |
| TC-18 | 08 | testdata run directory | `summarize --out tmp.json` twice | byte-identical files; each file's text == `json.dumps(json.loads(text), indent=2, sort_keys=True) + "\n"`; top-level keys `schema, corpus, lunarCommit, thresholds, usage, domains, verdicts, shapes, families, bugs`; `schema == 1` | `json.dumps` without `sort_keys` (keys stay in insertion order, `schema` before `bugs`) |
| TC-22 | 05, 06 | weights `x` 0.75, `y` 0.25, `z` unweighted; member `a` (`x`, 2 000 lines): `arg.table` cells pop 30 with 1 of 5 measured silent and pop 10 with 3 of 5, `return.mismatch` pop 10 with 0 of 2; member `b` (`y`, 1 000 lines): `arg.table` pop 10 with 2 of 2; member `c` (`z`, 10 000 lines): `arg.table` pop 1 000 with 0 of 4, `arg.class` pop 10 with 1 of 1 | `weights(...)["shapes"]`, then `render(...)` | `arg.table`: byDomain `x` `{exposurePerKloc 20.0, silentRate 0.30, missedPerKloc 6.0}`, `y` `{10.0, 1.0, 10.0}`, `z` `{100.0, 0.0, 0.0}`; pooled `{17.5, 0.475, 7.0}`. `arg.class` pooled `{0.0, null, null}`. `return.mismatch` pooled `{3.75, 0.0, 0.0}`. Reading 1 lists `arg.table`, `return.mismatch`, `arg.class` in that order | pooled `silentRate` as Σ population·rate over all domains' cells (`22/1 050 = 0.021`) |
| TC-23 | 07 | weights `x` 0.75, `y` 0.25, `z` unweighted; `x`: member `a` 2 000 lines, baseline `arity.tooMany` 4, member `b` 1 000 lines, 0; `y`: one member, 1 000 lines, 2; `z`: one member, 1 000 lines, 10 | `false_positives(members, w)["arity.tooMany"]` | `{"byDomain": {"x": 1.00, "y": 2.00, "z": 10.00}, "pooled": 1.25}` (pooled = 0.75·1.00 + 0.25·2.00) | lines pooled across members in `x` (`4/3 = 1.33`, pooled 1.50) |
| TC-24 | 06, 12 | TC-19's fixture domains plus `"excluded": [{"name": "e", "counts": {"s7": 5000}}]` | `coverage(domains)` | `(400 + 600) / (400 + 600 + 50 + 1000) = 0.4878` | excluded counts added to the denominator (`0.1418`) |
| TC-25 | 12 | fixture domains, in file order: `neovim` host sampled 940; `defold` host sampled 10; `h` host sampled 10; `u` host unsampled 30; `puc` interpreter sampled 10 | `share_crossings(domains)` | `["h: 1.00 % of measured usage crosses the 2 % sampling line; sampled stays true", "u: 3.00 % of measured usage crosses the 2 % sampling line; sampled stays false"]` | `NAMED_HOSTS` exemption dropped (adds a `defold` line) |

## Acceptance Criteria
- [ ] Every TC in the table passes; each named mutation, applied by hand, turns its case red.
- [ ] `results.md` exists with every section of MAINT-56-10 (and -11 for the Should).
- [ ] `tooling/corpus/rule-weights.json` is committed and validates against design §4.3.

## Non-Functional Requirements
- The representative run is opt-in and never shares a JVM with `-PwithCorpus` or `-PwithTypeAudit`
  (BUG-418). Its budget is ≤ 180 min on the builder (risks-and-gaps DR-04 holds the branch).
- Kotlin follows `docs/engineering-contract.md` (≤ 30 lines/function, ≤ 3 args, no `!!`).

## Dependencies
- MAINT-54 (done): harness, schema, `type_audit_summary.py`.
- MAINT-37 (done): definition-library provisioning used by the sweep.

## See Also
- Design: [design.md](design.md) · Plan: [implementation-plan.md](implementation-plan.md) ·
  Risks: [risks-and-gaps.md](risks-and-gaps.md)
