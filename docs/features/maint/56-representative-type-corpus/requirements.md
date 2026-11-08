---
id: "MAINT-56"
title: "56: Representative Type-Checking Corpus and Real-World Rule Frequencies"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-56: Representative Type-Checking Corpus and Real-World Rule Frequencies

Concept only. It is to be planned through `plan-feature` before any work starts. If the parts prove
independently shippable, split them per the split rule.

## Overview

Two questions decide how much each type-checking rule matters: how often real code gives the rule a
chance to fire, and how often real bugs of that kind are made. The current corpus cannot answer
either, and it also limits how far [[MAINT-54]]'s verdict can be trusted.

## Why the current corpus is not representative

The five members (luacheck, luarocks, penlight, lazynvim, zerobrane) are a good regression baseline,
but a poor sample of the code Lunar checks:

1. **Almost no annotations.** Type checking runs on LuaCATS annotations, and only lazynvim has them.
   The other four reach type checking only through standard-library calls, which is why 91% of
   MAINT-54's weight landed on bundled-stub callees.
2. **Mature tool code by expert authors.** It is heavily tested, defensively written and full of
   compatibility shims across Lua versions. Bug density is low, and the code is unlike application
   code.
3. **Missing domains.** There is no game code (LÖVE, Defold); no OpenResty, Redis or Tarantool
   scripts, although Lunar ships stubs for all three; no addon or embedding code; and no beginner code.
4. **Released code, not code being edited.** Every snapshot is a finished release whose bugs are fixed.
   An IDE checker matters most on code in progress.

MAINT-54's REOPEN verdict therefore describes "lazynvim plus standard-library calls in mature tools".
It stands as stated, but it is weaker evidence for a type-engine redesign than F = 0.79 suggests.

## Measurements

Each rule's importance is estimated as exposure × real-bug rate × catch rate, weighed against the rule's
false-positive rate.

1. **Exposure.** Count every site where each rule could apply (calls to annotated functions,
   table-typed parameters, typed field writes, possibly-nil receivers), reusing the MAINT-54 site
   finder.
2. **Real-bug rate by bug-fix mining.** For each fix commit in a corpus repository, run Lunar on the
   parent commit and record which rule, if any, fires on the lines the fix changed. That gives each
   rule's catch rate on real bugs, and the uncaught bugs show which rules are missing. This is the
   method of Gao, Bird and Barr, *To Type or Not to Type* (ICSE 2017), who found that TypeScript or
   Flow would have caught about 15% of the JavaScript bugs they studied.
3. **Runtime-error proxy.** Lua's runtime error messages fall into fixed categories (`attempt to index
   a nil value`, `attempt to call a nil value`, `bad argument #n to 'f' (x expected, got y)`). Counting
   them in issue trackers and on Q&A sites gives a rough distribution of real mistakes. It is noisier
   than measurement 2.
4. **False-positive rate.** Released code is assumed to run, so a finding on it is a suspected false
   positive. BUG-38's first attempt measured exactly this: 39 new findings, most of them on working
   code.

Weighting MAINT-54's mutation operators by measurement 2's bug distribution would turn its F from "how
often our chosen edits go unreported" into an estimate of how often real bugs slip through.

## Draft Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-56-01 | Stratified corpus | M | Not Implemented | Members are chosen per domain (Neovim plugins, OpenResty, game, Redis/Tarantool scripting, general libraries), by popularity, with some small or young projects in each. |
| MAINT-56-02 | Annotated code per domain | M | Not Implemented | Each domain includes LuaCATS-annotated projects, so it exercises more than standard-library calls. |
| MAINT-56-03 | Exposure counts | M | Not Implemented | The number of sites per rule and per domain, from the MAINT-54 site finder. |
| MAINT-56-04 | Bug-fix mining | S | Not Implemented | The parent of each fix commit is checked, and the catch rate per rule on the fixed lines is recorded. The Neovim plugin set comes first, since it carries the annotations. |
| MAINT-56-05 | Runtime-error proxy | C | Not Implemented | Counts of Lua runtime error categories from public issue trackers. |
| MAINT-56-06 | Weighted audit | S | Not Implemented | MAINT-54's F is recomputed with mutation operators weighted by MAINT-56-04's distribution. |

## Open Questions

- What corpus size is affordable? A full MAINT-54 run takes about 13 minutes for five members.
- How can fix commits be identified reliably (commit message, linked issue, or a revert of the change)?
- Which licences permit vendoring each member into `test/corpus`?
- Does the corpus extend the existing `test/corpus` baselines, or form a separate opt-in set like
  `-PwithTypeAudit`?
