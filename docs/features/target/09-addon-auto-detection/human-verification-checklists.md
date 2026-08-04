---
id: TARGET-09-CHECKLIST
title: "Verification Checklists"
type: qa
parent_id: TARGET-09
folders:
  - "[[features/target/09-addon-auto-detection/requirements|requirements]]"
---

# Verification Checklists: TARGET-09 — Definition-library Auto-detection

Manual, human-run scenarios in a live GoLand, per the `verify-in-ide` skill. **Not optional for
this feature**: a banner is registration-and-presentation, which is precisely the class of thing a
green unit suite does not prove — TARGET-08 shipped looking finished three times on that mistake.

Preconditions: a project with **no** definition libraries enabled, and `love2d` not cached. Rotate
`idea.log` before each run — it appends across runs, so `grep -m1` returns the oldest match.

## 1. Detection & suggestion

### Scenario 1.1: The banner appears on a file that uses the API
1. Open a `.lua` file containing `love.graphics.setColor(1, 1, 1)`.
2. **Expect** a banner: *"This file uses LÖVE (love2d). Enable its type definitions?"* with
   **Enable**, **Not now**, **Never for this project**.
3. **Expect** no network activity yet — nothing is fetched until a click.

### Scenario 1.2: No banner where there is nothing to suggest
1. Open a `.lua` file with no `love.` usage and no `require` of luassert.
2. **Expect** no banner.
3. Open a non-Lua file (e.g. `go.mod`). **Expect** no banner.

### Scenario 1.3: Enabling from the banner fetches and resolves
1. From Scenario 1.1, click **Enable**.
2. **Expect** the background task *"Lua: fetching definition libraries"*, then the banner to clear.
3. **Expect** `love2d` to appear under *External Libraries ▸ Lua Definition Libraries*, and
   `love.graphics.` to complete members.
4. Check `.idea/lunar.xml` after closing settings: `enabledDefinitionLibraries` contains `love2d`.

## 2. Dismissal

### Scenario 2.1: "Not now" is session-scoped
1. On the Scenario 1.1 banner, click **Not now**. **Expect** the banner to clear.
2. Close and reopen the file. **Expect** the banner **not** to reappear (same session).
3. Restart the IDE and reopen the file. **Expect** the banner **to** reappear.

### Scenario 2.2: "Never for this project" persists
1. Click **Never for this project**. **Expect** the banner to clear.
2. Read `.idea/lunar.xml` — **expect** `dismissedDefinitionLibraries` to contain `love2d`.
   (Read the XML, not the UI: settings panels do not re-render after Apply.)
3. Restart the IDE and reopen the file. **Expect** no banner.

### Scenario 2.3: Enabling elsewhere suppresses the suggestion
1. With a fresh project, enable `love2d` from the settings page instead of the banner.
2. Open a file using `love.graphics`. **Expect** no banner — there is nothing left to suggest.

## 3. Robustness

### Scenario 3.1: Offline
1. With egress blocked, trigger the banner and click **Enable**.
2. **Expect** the existing TARGET-08 failure balloon, and the id to stay enabled for retry.
3. **Expect** no exception dialog and no editor breakage.

### Scenario 3.2: The editor stays responsive and the log stays clean
1. Type continuously in a large `.lua` file that triggers the banner.
2. **Expect** no freeze and no repeated banner flicker.
3. `grep -c "Slow operations are prohibited" idea.log` → **0**;
   `grep -c "at net.internetisalie" idea.log` → **0**.
