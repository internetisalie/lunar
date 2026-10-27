---
id: "BUG-535"
title: "Provisioning sets a progress fraction on an indeterminate indicator, which the platform logs as IllegalStateException"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-535: progress fraction on an indeterminate indicator

Seen during the BUG-533/534 live checks, on GoLand 2026.1.3 and 2026.2.3.

## 1. Reproduction
Create a LuaRocks project via File → New → Project… → LuaRocks, with "Provision isolated
environment" ticked. Then read `idea.log`.

## 2. Expected vs Actual Behavior
- **Expected**: provisioning reports progress without errors.
- **Actual**: the log records an `IllegalStateException` at INFO level from `setFraction`, raised
  when it is called on an indeterminate progress indicator. Provisioning itself succeeds.

## 3. Context / Environment
- **Relevant Files**:
  `src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaProvisionEngine.kt:175`
  (`setup.job.indicator.fraction = index.toDouble() / items.size`). The indicator is never switched
  to determinate (`isIndeterminate = false`) first.
- **Not established**: the exact log line was not kept. It was reported by the BUG-533/534
  implementer and not re-captured.
