---
id: "MAINT-43"
title: "43: CI Image Publish Job"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "low"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-43: CI image publish job

## The gap

`lunar-ci`, the job image the routine CI pool runs (`tooling/ci-image/Dockerfile`), is built and
pushed by hand. The pool's label is tagless and resolves to the newest semver in GAR, so a push
deploys straight to `main` and every open PR. MAINT-21 publishes `0.3.0` through a checkpointed
manual sequence; nothing automates it.

## Decided shape (user, 2026-10-25)

- **The job publishes to tier2 only:** `registry.internetisalie.net/lunar/lunar-ci`. That is an
  anonymous push and needs no credentials.
- **The copy to GAR stays a deliberate manual step:** `crane copy` from tier2 to
  `northamerica-northeast1-docker.pkg.dev/cosmic-region-400615/lunar/lunar-ci:<semver>`, run by the
  supervisor. The copy is the deploy, so no GCP write identity is created for CI.
- **Pattern:** loom-delivery's runner-image lane (`docs/features/ci/delivery-runner-image/design.md`
  in loom). On a `v*`-style image tag it:
  1. refuses a version lower than the highest already published;
  2. builds and pushes;
  3. captures the digest and pulls the image by that digest to verify it;
  4. records the digest.

## Scope to plan

- **Trigger:** which tag or ref starts the job, kept separate from the plugin's `v*` release tags.
- **Runner:** where the job runs. Note that `lunar-ci` has no Docker or step engine.
- **The version gate's source of truth:** tier2 tags, GAR tags, or both.
- **Hand-over:** how the digest reaches the manual GAR copy, and the copy's own monotonic check.
- **Workflows:** both workflow pairs, or Gitea only, with the reason.

Not planned. Plan with `plan-feature`.
