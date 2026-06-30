---
id: "SYNTAX-07-07-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SYNTAX-07-07"
priority: "medium"
folders:
  - "[[features/syntax/07-inlay-hints/07-07-method-chaining-hints/requirements|requirements]]"
---

# Implementation Plan: SYNTAX-07-07 Method Chaining Hints

## Phase 1: Foundation & Infrastructure
- [ ] Create `LuaMethodChainInlayHintsTest` to establish baseline behavior.
- [ ] Implement `LuaInlayTypeUtil.formatReturnTypes()` to centralize return type stringification.
- [ ] Implement a utility to detect multi-line method chains from a `LuaFuncCall`.

## Phase 2: Core Implementation
- [ ] Create `LuaMethodChainInlayHintProvider` and register it in `plugin.xml` under `METHOD_CHAINS_GROUP`.
- [ ] Implement the collector to visit `LuaFuncCall` and emit chaining hints.
- [ ] Implement `self` type resolution to map to concrete receiver classes.
- [ ] Integrate generic instantiation into the hint resolution loop.
- [ ] Implement the logic to position hints after the closing parenthesis of the call.

## Phase 3: Refinement & Optimization
- [ ] Implement suppression for same-line calls within a chain.
- [ ] Add trivial type suppression (boolean, number).
- [ ] Implement a depth limit (10) for chain resolution to ensure performance.
- [ ] Implement union type truncation for complex returns.

## Phase 4: Verification
- [ ] Ensure all test cases in the requirements spec pass.
- [ ] Perform manual verification in complex Lua projects (e.g., using a mock builder API).
- [ ] Check performance on files with deep chains.
