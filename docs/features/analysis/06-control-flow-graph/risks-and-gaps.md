---
id: ANALYSIS-06-RISKS
title: Control Flow Graph (CFG) Risks
type: risks
parent_id: ANALYSIS-06
status: planned
---

# Risks and Gaps

## 1. Lua `goto` Statement Complexity
**Risk:** Medium. Lua 5.2+ supports `goto`, which allows jumping forward and backward, potentially creating complex loops and irreducible CFGs.
**Mitigation:** `ControlFlowBuilder` must maintain a pending resolution table for `goto` labels to resolve forward jumps at the end of block processing.
