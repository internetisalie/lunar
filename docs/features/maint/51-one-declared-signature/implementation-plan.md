---
id: "MAINT-51-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-51"
folders:
  - "[[features/maint/51-one-declared-signature/requirements|requirements]]"
---

# MAINT-51: Implementation Plan

One phase, one PR, on top of [[MAINT-52]]'s merged code. Run every Gradle invocation on the libvirt
builder, with `--rerun --no-build-cache` for tests. Use
`~/.cache/claude-scratch/lunar/bin/builder-run libvirt "<args>"`, except for the recording in task 7,
which also copies files back and so takes the lock itself.

## Phase 1: One declared signature [Must]

### Tasks
1. **Rules.** Add `parameterDemand` and `informativeArms` to `LuaTypeAlgebra` (design §2.1), code and
   KDoc, after `isOptional`.
2. **Materialized and in-file sites.** Make design §2.2 in `LuaGraphType.declaredParamNodeFor` and
   `LuaTypeGraphBridge`. Remove the `LuaCatsParamTag` import from `LuaTypeGraphBridge.kt`: at `dccb8b18`
   `demandTypeFor` is its only user.
3. **Copier.** Create `LuaSignatureCopier.kt` (design §2.3). Cut the functions design §2.3 lists from
   `LuaTypesVisitor` and paste them into it with the edits §2.3 lists; do not retype them.
4. **Visitor.** Make design §2.4: the `signatureCopier` field, the new `demandDeclaredParams` body,
   and the deletion of `demandTypeOf`.
5. **`nil` rule.** Make design §2.5 steps 1-3 in `LuaTypeGraph`, code and comments.
6. **Tests.** Write `LuaDeclaredSignatureTest` (design §4) with TC-01 to TC-16. Copy each input and
   expected list from `requirements.md` exactly; line breaks are written there as ` / `.
7. **Re-record.** The builder's rsync is one-way, so each recording is copied back inside the
   backend's lock, where no other run can resync the VM in between. From the worktree root:

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

   The corpus is recorded in the shape CI's ratchet runs (`--tests '*Corpus*'`), because a recording
   made in another shape does not hold (BUG-418). `git status` must list no untracked file. The diff
   must be exactly the re-record rows in `risks-and-gaps.md`, and nothing else; no corpus baseline is
   among them (TC-18). If anything else differs, stop and report it as a finding;
   do not commit it.
8. **TYPE-14.** Make the edits in design §5 to `docs/features/type/14-generics-baseline/design.md`
   and `implementation-plan.md`, then run TC-17's commands.
9. **Changelog.** Under the current milestone's *Fixed* heading: "An argument that may be `nil` on
   some paths, or an array or record with `nil` among its element or field types, is no longer
   reported against a parameter declared as a union." Follow the `changelog` skill.

### Verification
- **Mutations.** Apply each of design §6's mutations M1, M2, N1, N2, N4 and N5 in turn, run
  `test --rerun --no-build-cache --tests '*LuaDeclaredSignatureTest*'`, and confirm that the TCs
  naming that mutation fail with the stated output. For M6, run
  `test --rerun --no-build-cache --tests '*ExpectedCallbackResolverTest*' --tests '*LuaCrossFileFunctionDemandTest*'`
  and confirm TC-19. For M7, run the corpus command below and confirm TC-18. Restore with the
  `temporary-edits` skill.
- **Full suite.** On a branch rebased onto `origin/main` with MAINT-52 merged, `test --rerun --no-build-cache`: 0 failures, `LuaUnknownRuleTest` included. No test is exempt.
- **Corpus.** In CI's shape, `test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*'`:
  `LuaCorpusSweepTest` and `LuaTortureCorpusTest` appear in `build/test-results/test/` with fresh
  timestamps, and none fails (TC-18).
- **Lint.** `ktlintCheck` alone, after formatting on the VM if needed. Never pair it with `ktlintFormat`.
- **Grep.** The acceptance-criteria greps in `requirements.md`.

### Exit criteria
- Every TC in `requirements.md` passes, and every mutation in design §6 reddens the TCs that name it.
- The `src/test/resources` diff equals the re-record rows in `risks-and-gaps.md`.
- Full suite and corpus green; `ktlintCheck` green.
