---
id: "BUG-421"
title: "68 wildcard imports keep `no-wildcard-imports` disabled, against the engineering contract"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-421: Wildcard imports keep a contract rule switched off

[`docs/engineering-contract.md`](../../../engineering-contract.md) requires **no wildcard imports**.
When the ktlint standard ruleset was adopted (2026-08-07), `standard:no-wildcard-imports` was the
one rule left disabled, because 68 wildcard imports across 57 files could not be expanded safely.

This is recorded as debt rather than a decision: the rule *should* be on, and the `.editorconfig`
entry says so and points here. Every other exception there is a deliberate, permanent one.

## Why it was not simply fixed

ktlint cannot auto-fix this rule — expanding `import pkg.*` requires knowing which symbols the file
actually resolves from `pkg`, which is compiler-level information. Hand-editing 68 imports across 57
files risks silently changing resolution (a name that currently resolves via the wildcard could
start resolving elsewhere, or fail to compile in a configuration not covered by the suite).

## Scope, measured

```
27  net.internetisalie.lunar.lang.psi.*          (generated PSI interfaces — the large one)
 5  org.junit.jupiter.api.Assertions.*
 4  net.internetisalie.lunar.luacats.lang.psi.*
 3  net.internetisalie.lunar.lang.psi.types.*
 3  net.internetisalie.lunar.lang.indexing.*
 3  java.util.*                                  (allowed by ij_kotlin_packages_to_use_star_imports)
 3  com.intellij.psi.stubs.*
 3  com.intellij.psi.*
 3  com.intellij.codeInsight.hints.declarative.*
 …  tail of 1–2 occurrences each
```

Note `java.util.*` is explicitly permitted by `.editorconfig`'s
`ij_kotlin_packages_to_use_star_imports`, so those are not violations of the project's own policy —
only of ktlint's default. Whether that allowance stays is part of this fix's decision.

## Fix direction

The reliable route is an IDE-assisted **Optimize Imports** over the affected files (IntelliJ resolves
each symbol and writes the explicit list), then `ktlintCheck` with the rule re-enabled, then the full
suite. Doing it file-by-file keeps each step reviewable and lets a compile failure name its own file.

Re-enable by deleting the `ktlint_standard_no-wildcard-imports = disabled` entry in `.editorconfig`.

## Impact

Low. Wildcard imports are a readability and resolution-stability concern, not a correctness one, and
nothing is currently broken by them. It matters because it is the only ktlint exception that is not
a deliberate, permanent choice — leaving it unrecorded is how it would quietly become one.
