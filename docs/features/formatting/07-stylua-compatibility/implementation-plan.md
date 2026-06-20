---
id: "FORMAT-07-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "FORMAT-07"
folders:
  - "[[features/formatting/07-stylua-compatibility/requirements|requirements]]"
---

# FORMAT-07: Implementation Plan

## Phases

### Phase 1: Core formatting service [Must]
- **Goal**: A working `StyluaFormattingService` that can format Lua files via the Stylua
  CLI when a valid binary is bound, and a full automated test suite covering all Must
  requirements.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.formatting.external.StyluaFormattingService`
    extending `AsyncDocumentFormattingService` (design §2.1) — implements `canFormat()`
    (§3.1), `getFeatures()` (§3.2), `createFormattingTask()` (§3.3, §2.2), `getName()`,
    `getNotificationGroupId()`, and `prepareForFormatting()`.
  - [ ] Create `net.internetisalie.lunar.lang.formatting.external.StyluaFormattingTask`
    (design §2.2) — implements `FormattingTask.run()` (§3.3 CLI invocation) and `cancel()`
    (§3.4). Reads stdin from `request.getDocumentText()`, writes to process, captures
    output via `LuaProcessUtil.capture()`, dispatches `onTextReady` / `onError`.
  - [ ] Register `<formattingService>` in `src/main/resources/META-INF/plugin.xml`
    (design §7.1).
  - [ ] Register `<notificationGroup id="notification.group.lunar.stylua">` in `plugin.xml`
    (design §7.2).
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/lang/formatting/external/StyluaFormattingServiceTest.kt`
    — covers all 6 Must test cases (TC 1–6) from requirements.md plus the FORMAT-07-05
    notification case.
  - [ ] Create a small test-fixture lua file and a mock `stylua` shell script (or use
    `myFixture.tempDirFixture` to create a controlled binary) that the test can bind via
    `LuaToolManager` to test the integration end-to-end.
- **Exit criteria**: All tests pass green. The existing formatting test suite
  (wave 7 tests) continues to pass — the built-in formatter is not broken.

### Phase 2: First-use notification [Could]
- **Goal**: Show a non-blocking notification when Stylua successfully formats a file for
  the first time in this IDE session.
- **Tasks**:
  - [ ] In `StyluaFormattingTask.run()`, after a successful `onTextReady()`, check
    `PropertiesComponent.getInstance().getBoolean("lunar.stylua.firstUse.notified")`.
    If `false`, set it to `true` and post a notification through
    `NotificationGroupManager.getInstance().getNotificationGroup("notification.group.lunar.stylua")`.
  - [ ] Add test case TC-7: verify notification fires only once.
- **Exit criteria**: TC-7 passes. Manual verification shows notification on first use.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| FORMAT-07-01 | M | Phase 1 |
| FORMAT-07-02 | M | Phase 1 |
| FORMAT-07-03 | M | Phase 1 |
| FORMAT-07-04 | M | Phase 1 |
| FORMAT-07-05 | C | Phase 2 |

## Verification Tasks
- [ ] Run `./gradlew test --tests "*.StyluaFormattingServiceTest"` — covers TC 1–7
- [ ] Run `./gradlew test --tests "*.TestLuaFormattingWave7"` — verifies no regression
- [ ] Run human-verification-checklists.md against the sandbox IDE
- [ ] Confirm `plugin.xml` entries are present and valid

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Core formatting service | todo | Must |
| Phase 2: First-use notification | todo | Could |
