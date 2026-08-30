---
id: "MAINT-39"
title: "39: A LuaCATS-Annotated Corpus"
type: "feature"
parent_id: "MAINT"
status: "blocked"
priority: "high"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-39: A LuaCATS-Annotated Corpus

## The gap

The corpus sweep is **structurally incapable** of exercising Lunar's LuaCATS subsystem. Measured
2026-08-29:

| | |
| :--- | ---: |
| corpus files | **734** |
| files carrying **any** `---@` annotation | **0** |
| colon-call tokens | 16 336 |
| colon-method declarations | 809 |

The corpus is `luacheck`, `luarocks`, `penlight` and `zerobrane` — all **pre-LuaCATS-era** Lua,
written before the annotation dialect existed. So `@class`, `@alias`, `@field`, `@type`, `@param`
and `@return` are never seen by a sweep, and everything under
`src/main/kotlin/net/internetisalie/lunar/luacats/` plus the annotated half of the type engine is
validated by unit fixtures alone.

**This is not hypothetical.** [[BUG-473]] — one `---@class` making `LuaTypesSnapshot.forFile`
superlinear, 21 s on a realistically-sized file — lived there undetected and was found by a
de-risking spike, not by the sweep whose job it is. A green corpus run says nothing about the
annotated path, and this session repeatedly cited such runs as evidence.

## Scope

Add real, natively-annotated Lua to the corpus, and the sweep coverage to exercise it. Candidate
sources, in rough order of annotation density:

- the **`LuaCATS/*`** definition repositories — near-pure annotation, the reference dialect
- **`lua-language-server`**'s own definition and test corpus — the dialect's implementation
- the **Neovim plugin ecosystem**, where LuaCATS is used natively and densely at application scale —
  `lazy.nvim`, `telescope.nvim`, `plenary.nvim`, `nvim-lspconfig`

Licensing and pinning must be settled per source, as [[MAINT-37]] does for definition libraries.

## The ordering constraint — read before scheduling

**[[BUG-473]] blocks this, and this is what would validate [[BUG-473]].** Adding annotated corpus
today produces a sweep that does not finish: after Phase 1 the annotated snapshot still grows ×8.1
per doubling, 21 s at 160 call sites, and 16 of 800 real files already exceed that. A sweep that
times out gets disabled, and a disabled sweep is worse than the current blind one.

So the work is staged, and **the stages are not reorderable**:

| # | Step | State | Why it must come first |
| :-- | :--- | :--- | :--- |
| 1 | [[BUG-473]] **DR-6** — one small annotated fixture | **Done** 2026-08-29 | Enough to regression-detect BUG-473; cheap enough to run today |
| 2 | [[BUG-473]] **S6** sized, and the curve made tolerable | Open | Real annotated projects are unsweepable until the exponent moves |
| 3 | **This feature** — real LuaCATS projects | Blocked on step 2 | Only affordable once step 2 lands |

**Step 1 landed as** `src/test/resources/corpus/annotated/` (`builder.lua`, a `---@class` with 40
colon-call sites; `shapes.lua`, the `@alias` / `@field` / `@class X : Y` / `@param` / `@return` /
`@type` vocabulary) plus `LuaAnnotatedFixtureSweepTest`, opt-in with `-PwithCorpus`. It gates on
deterministic walk-root resolution counts rather than a duration, and costs 2.36 s of an 18-minute
corpus run. The measurement that sized it, and the three things it deliberately does not cover, are
in [[BUG-473]] under "DR-6 — executed".

**Step 1 does not shrink step 3.** The fixture is synthetic and single-shaped; it says nothing about
inheritance chains, `@generic`, `@overload`, cross-module `@class` or `@alias` unions, and it carries
no ratcheted baseline, because an in-repo tree is not a `CorpusEntry` and `CorpusSweep.run` requires
a manifest pin and a fetched checkout.

## What step 3 is expected to surface

The synthetic fixture is **one class and *n* calls**. Real annotated code is not that shape, and the
shapes it adds are the ones nothing has ever exercised: inheritance chains, `@generic`, `@overload`,
cross-module `@class`, `@alias` unions, and `@field` tables. **Expect this to find defects rather
than confirm health** — that is the reason to do it, and a plan that assumes a clean first sweep is
mis-scoped.

## Relationship to neighbouring work

- **[[MAINT-37]]** pins *definition libraries* for existing sweeps. Different problem: that is about
  reproducibility of what is already run; this is about running something that is not.
- **[[MAINT-38]]** gave the project time attribution. Without it, a slow annotated sweep could not be
  diagnosed, only observed.
- **[[REFACT-08]]** (rename a LuaCATS type name) is blocked on a renameable symbol that does not
  exist; a corpus carrying real type names is what would regression-test it once it does.
