---
id: "BUG-445"
title: "The documented `ktlintFormat` step formats the builder VM's copy and never reaches the repo"
type: "bug"
parent_id: "BUG"
status: "done"
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

## Fix

Both halves, together, so the gate is never added to a repo it would immediately fail.

**1. CI gates on `ktlintCheck`** — `build-plugin.yml`, both platforms, both build lanes:

| lane | placement | why there |
| :-- | :-- | :-- |
| Gitea `build` (`lunar-ci`) | after compile, before the suite | failure is written to `/tmp/lunar-ktlint-failed` and reported by the final verdict step, alongside the test verdict. The executor rejects step-level `if:`, and under `sh -e` a failing step aborts the job script — a formatting nit would otherwise throw away the suite, the coverage report and the analytics ingest |
| Gitea `build-release` | after the analytics ingest, before the artifact upload | tests, coverage and ingest have all run, so the failure costs no diagnostics. It still drops `if: success()` on the upload, so an unformatted tree cannot be published and `release` never starts |
| GitHub `build` | after the suite, before the artifact upload | same, and `Upload test reports` is `if: always()` so reports survive |

The platforms differ in *how* the failure is carried and not in what a green build means: both fail
the job when, and only when, `ktlintCheck` fails. The GitHub file's header records the difference in
its existing list.

**2. The debt is cleared** in the same change — 13 files, `ktlintFormat` output verbatim. Proven to
be formatting-only: with whitespace, commas and `import` lines stripped, every file is byte-identical
to its previous version. The only net semantic change is the removal of four genuinely unused
`import com.intellij.openapi.vfs.VirtualFile` lines left behind by BUG-436, which the contract asks
for anyway; the two other import diffs are re-orderings, removed and re-added.

**3. The instruction that caused it is corrected** in `.agents/AGENTS.md` — both places that
prescribed `run "ktlintFormat ktlintCheck"`. It now says to run `ktlintCheck` **alone**, explains why
the pairing cannot fail, and gives the format-then-pull-back recipe:

```bash
tooling/gce-builder/gce-builder.sh run ktlintFormat
rsync -az --include='*/' --include='*.kt' --exclude='*' builder:/home/builder/lunar/src/ src/
```

That file is **gitignored and local-only** (`.gitignore:13`), so unlike the other two halves the
correction does not travel with the repo — it applies to this machine's checkout only. Anyone
cloning fresh inherits CI's gate but not the corrected local advice, which is a gap this repo's
`.agents/` convention creates and cannot fix from inside a commit.

## Verification

`ktlintCheck` **alone** — never paired with `ktlintFormat`, which is the whole point — run against
the reformatted tree, together with the full suite so the import removals are proven by compilation.
