---
id: "BUG-445"
title: "The documented `ktlintFormat` step formats the builder VM's copy and never reaches the repo"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-445: the lint gate reports green while the repo stays unformatted

Found 2026-08-21 during [[BUG-422]]'s verification.

## The gap

`AGENTS.md` prescribes this before committing:

```bash
tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"
```

`gce-builder.sh` rsyncs the working tree **to** the VM and never syncs back — the transport is
one-directional by design (`rsync -az --delete` local → remote, no reverse leg). So `ktlintFormat`
rewrites the *VM's* copy, `ktlintCheck` then passes against those rewritten files, and the local
working tree — the one that gets committed — keeps every violation it had.

The command therefore cannot fail, and its green result carries no information about what is about to
be committed. This is the same class of defect as the `--rerun` one already recorded in `AGENTS.md`
(a job reporting a pass having executed nothing), and it went unnoticed for the same reason.

## Measured

Running `ktlintCheck` **alone** (no `ktlintFormat`) against `main` @ `9c78a037`:

```
> Task :ktlintCheck FAILED
Lint has found errors than can be autocorrected using 'ktlint --format'
```

13 files, all landed by recent bug-fix commits:

| area | files |
| :-- | :-- |
| `lang/indexing` | `LuaReceiverMemberIndex`, `LuaDescriptionIndex`, `LuaFileBindingsIndex`, `LuaMemberFieldIndex`, `LuaCatsTypeNameIndex`, `LuaGlobalAssignmentIndex` |
| `lang` | `LuaCompletionContributor`, `LuaDocumentationTargetProvider`, `psi/types/LuaTypeGraph` |
| tests | `LuaFileTypeRegistrationIndexTest`, `FreeGlobalMemberTypingTest`, `LuaNestedMemberAssignmentTest`, `LuaUnknownProvenanceTest` |

Violations are ordinary mechanical ones — `argument-list-wrapping`, `chain-method-continuation`,
`multiline-expression-wrapping`, `blank-line-before-declaration`, and a few `max-line-length`.

**CI does not cover this**: neither `.gitea/workflows/` nor `.github/workflows/` runs `ktlintCheck`,
so nothing else catches the drift. The repo's claim that the check "is a real gate" is currently only
true of a command that cannot fail.

## Fix direction

Two parts, and the second is the one that matters:

1. **Clear the debt.** `ktlintFormat` is mechanical and ktlint's output *is* the house style, so this
   is a formatting-only commit — kept separate from any behavioural change, per the standing "no
   mass-reformatting inside a fix" rule.
2. **Make the gate able to fail.** Either run `ktlintCheck` **without** `ktlintFormat` as the
   pre-commit step (so it reports on what will actually be committed), or give `gce-builder.sh` a
   sync-back leg for source files after a formatting task. Correct `AGENTS.md` either way — the
   current instruction is the defect, not a mistake by whoever followed it.

Adding `ktlintCheck` to CI would close it permanently, but only once the debt above is cleared.
