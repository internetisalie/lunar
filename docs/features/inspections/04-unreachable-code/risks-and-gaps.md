---
id: INSP-04-RISKS
title: Unreachable Code Risks
type: risks
parent_id: INSP-04
status: planned
---

# Risks and Gaps

## 1. Missing Control Flow Graph (CFG)
**Risk:** High. The JetBrains standard for unreachable code (and unused locals) strictly relies on a CFG to track paths. Lunar does not have a CFG.
**Mitigation:** We must build a `ControlFlowBuilder` first. This is a significant architectural addition. Added to Phase 0 of the implementation plan.
