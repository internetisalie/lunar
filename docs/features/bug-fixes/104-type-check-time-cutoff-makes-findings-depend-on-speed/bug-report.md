---
id: "BUG-104"
title: "Type checking stops after 5 s of wall-clock time, so a large file's findings depend on machine speed"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-104: A wall-clock cutoff makes type-check findings depend on machine speed

## 1. Reproduction
`LuaTypeGraph.checkTypes` (`lang/psi/types/LuaTypeGraph.kt:297-324`) runs its fixed-point loop
until nothing changes, but breaks after `timeLimitMs = 5000` of wall-clock time and logs
`Type checking exceeded time limit (5000ms)`. Production callers use the default.

Analyse luals `script/core/compiler.lua` in the MAINT-60 sweep several times (BUG-103 §7.1):
- with the limit raised to 600 000 ms, every run gives 311 findings with digest `-890606056`;
- at the default limit, runs give `-890606056`, `722665400` (also 311 findings), or 302 findings
  when the JVM is slower (the walk kept out of the JIT), with the cutoff logged 8 times.
`infer.lua` moves the same way.

## 2. Expected
A file's findings depend only on the project's contents. A safety cutoff, if one is needed, is
deterministic, for example a count of iterations or pairs, so every machine and every run gives the
same answer.

## 3. Actual
On a file whose fixed point takes more than 5 s, the findings are whatever the loop reached when the
clock ran out. That varies with CPU speed, machine load, JIT warm-up and other work in the IDE.

## 4. Impact
- Users on slower machines, or under load, see different findings from users on fast ones, and the
  same user sees them change between runs.
- Any digest comparison on luals (the MAINT-60 sweep's digests, MAINT-65's alone digests) can report
  a change that comes from timing alone. MAINT-63's after-edit oracle skips luals for this reason
  (MAINT-63 risks Risk 1.5).
