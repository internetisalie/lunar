---
id: "MAINT-52-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-52"
folders:
  - "[[features/maint/52-one-unknown-rule/requirements|requirements]]"
---

# MAINT-52: Implementation Plan

One phase, one PR. Run every Gradle invocation on the libvirt builder, with `--rerun --no-build-cache`
for tests. Use `~/.cache/claude-scratch/lunar/bin/builder-run libvirt "<args>"`, except for the two
recordings in task 6, which also copy files back and so take the lock themselves.

## Phase 1: One unknown rule [Must]

### Tasks
1. **Union rule.** Make the `LuaTypeAlgebra` changes in design §2.1, code and KDoc. Replace the
   `LuaTypeAlgebraTest` case named in design §4 with TC-01.
2. **Conversions.** Make design §2.2, §2.3 and §2.4, code and comments.
3. **Field rule.** Make design §2.5.
4. **Comments.** Make design §2.6.
5. **Tests.** Write `LuaUnknownRuleTest` (design §4) with every TC that `requirements.md` places in
   it. Copy each input from `requirements.md` exactly. Line breaks are written there as ` / `. TC-06 is
   an existing test and needs no code.
6. **Re-record.** The builder's rsync is one-way, so each recording is copied back inside the
   backend's lock, where no other run can resync the VM in between. Record the corpus baselines in
   the shape CI's ratchet runs (`--tests '*Corpus*'`, `.gitea/workflows/build-plugin.yml:468`),
   because a recording made in another shape does not hold (`build.gradle.kts`, BUG-418). From the
   worktree root:

   ```bash
   GCE_BUILDER_BACKEND=libvirt flock ~/.cache/claude-scratch/lunar/builder-libvirt.lock bash -c '
     source tooling/gce-builder/config.sh
     SSH="ssh -i $SSH_KEY -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes -o LogLevel=ERROR"
     tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PwithCorpus -PrecordCorpusBaseline --tests '\''*Corpus*'\''"
     rsync -az -e "$SSH" "$REMOTE_USER@$LV_SSH_HOST:$REMOTE_DIR/src/test/resources/corpus/" src/test/resources/corpus/
     tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PrecordTypeGolden --tests '\''*TypeGolden*'\'' --tests '\''*MemberEnumerationGoldenTest*'\''"
     rsync -az -e "$SSH" "$REMOTE_USER@$LV_SSH_HOST:$REMOTE_DIR/build/type-golden/recorded/" src/test/resources/types-golden/'
   git diff --stat -- src/test/resources/
   git diff -- src/test/resources/
   git status --porcelain -- src/test/resources/
   ```

   `git status` must list no untracked file: `build/` survives the sync, so a stale recording from
   another branch would come back untracked. The diff must be exactly the re-record rows in `risks-and-gaps.md` (DR-03), and nothing else. If
   anything else differs, stop and report it as a finding; do not commit it.
7. **Changelog.** Under the current milestone's *Fixed* heading: "A table is no longer reported for
   missing a field the class declares as `any`, and an argument to an in-file function's
   `---@param o? unknown` is no longer reported as not assignable to `nil`." Follow the `changelog` skill.

### Verification
- **Mutations.** Apply each of design §6's mutations in turn, run
  `test --rerun --no-build-cache --tests '*LuaUnknownRuleTest*' --tests '*LuaTypeAlgebraTest*' --tests '*LuaMethodArgumentTypeTest*'`,
  and confirm that the TCs naming that mutation fail with the stated output. Restore with the
  `temporary-edits` skill.
- **Full suite.** `test --rerun --no-build-cache`: 0 failures.
- **Corpus.** `test -PwithCorpus --rerun --no-build-cache`: `LuaCorpusSweepTest`,
  `LuaTortureCorpusTest` and `LuaInspectionParityTest` appear in `build/test-results/test/` with fresh
  timestamps, and none fails.
- **Lint.** `ktlintCheck` alone, after formatting on the VM if needed. Never pair it with `ktlintFormat`.
- **Grep.** `git grep -n "it == Undefined) Any\|graphType != Undefined" -- src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaGraphType.kt`
  prints nothing. `git grep -n "carriedPart == LuaGraphType.Undefined\|declared != LuaGraphType.Undefined\|private fun isOptional" -- src/main`
  prints nothing.

### Exit criteria
- Every TC in `requirements.md` passes, and every mutation in design §6 reddens the TCs that name it.
- The `src/test/resources` diff equals DR-03's re-record rows.
- Full suite and corpus green; `ktlintCheck` green.
