---
id: "AI-04"
parent_id: "AI"
type: "feature"
status: "todo"
priority: "medium"
folders:
  - "[[features/ai/requirements|requirements]]"
title: "AI-04: LuaCATS Annotation Generator"
---

# AI-04: LuaCATS Annotation Generator (Type Migration Assistant)

**Requirement**: IDE actions that bulk-generate LuaCATS annotations from the type engine's
inference — a dynamic→typed migration assistant — plus a deterministic EmmyDoc→LuaCATS
converter. Optional LLM polish for names/descriptions only, off by default.
**Priority**: Should
**Status**: Not Implemented

---

## Overview

The one human-facing "AI" feature in the epic, chosen because it is mostly *not* AI: the
type engine already infers parameter, return, field, and class shapes; this feature writes
that inference down as LuaCATS annotations. Dynamic→typed migration tooling drove large
adoption waves in the TypeScript ecosystem; nothing comparable exists for Lua. The
deterministic core requires no network and no model.

### Strands

1. **Annotate with LuaCATS** — an action on a function / file / directory scope that
   inserts inferred `@param` / `@return` annotations (and `@class` / `@field` for
   implicitly-defined classes: `local M = {}` module tables, `self.x =` field discovery).
   Existing annotations are never overwritten; only missing ones are added. Where
   inference yields no information, the annotation is written as `any` with an optional
   `--- TODO: refine` marker (setting-controlled) so migration progress is greppable.
2. **EmmyDoc → LuaCATS converter** — deterministic translation of EmmyLua-dialect
   annotations (legacy `@param x number @desc` trailing-description form, EmmyLua-specific
   tag spellings and arrow function syntax) into Lunar's LuaCATS forms. The onboarding
   ramp for EmmyLua-plugin refugees (the largest competitor install base).
3. **Optional LLM polish** — a clearly-labeled, **off-by-default** hook that improves
   *descriptions and suggested names only* (never types), delegating to a user-configured
   provider (BYOK or the JetBrains AI Assistant API when present). The feature is fully
   functional with the hook disabled; no Lunar code embeds a vendor.

## Acceptance Criteria

- [ ] "Annotate with LuaCATS" action available on a function (intention/context menu), a
      file, and a directory scope (bulk mode)
- [ ] Generated annotations come from the type-engine snapshot: `@param` (with optionality
      `?` where inferred nilable), `@return` (multi-return aware), `@class`/`@field` for
      implicit module/class tables; rendered types use canonical union formatting
- [ ] Idempotent and non-destructive: existing annotations (any tag) are preserved
      untouched; re-running the action on an annotated scope is a no-op
- [ ] Unknown inference → `any`, with the optional TODO-marker setting; the action reports
      a summary (N functions annotated, M placeholders) on completion
- [ ] Bulk mode shows a preview (standard refactoring preview/diff) before writing; the
      whole batch is a single undoable write command; document formatting of touched
      comments matches the formatter settings
- [ ] EmmyDoc→LuaCATS converter: file- and directory-scope action with preview; conversion
      table covering the EmmyDoc trailing-description form, tag-spelling variants, and
      function-type syntax differences documented in the design doc; unconvertible
      constructs are left in place and listed in the completion summary — never silently
      dropped
- [ ] LLM polish hook: disabled by default; settings clearly state what is sent
      (signatures + existing comment text, never whole files) and to which configured
      provider; types produced by inference are never altered by the hook; the action
      works identically (minus polish) with the hook disabled or the provider unreachable
- [ ] No network access in the default configuration (verified by test: annotation run
      with network access denied succeeds)
- [ ] Unit tests: inference→annotation rendering matrix (params/returns/unions/optionals/
      varargs/classes), idempotency, EmmyDoc conversion table, preview correctness;
      performance guard for directory-scope runs (background task, cancellable, progress)
