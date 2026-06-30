---
id: NAVIGATION-12-RISKS
title: "12: Member Field Resolution — Risks & Gaps"
type: risk
parent_id: NAVIGATION-12
folders:
  - "[[features/navigation/12-member-field-resolution/requirements|requirements]]"
---
# Risks & Gaps: NAV-12 Member Field Resolution

| # | Risk / Gap | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| 1 | **Receiver text vs type.** Resolution keys on the literal receiver text (`package.path`), so `local p = package; p.path` won't resolve via the field index. | Some indirections miss. | Out of scope (Non-Goal). The bundled stdlib uses literal receivers, covering the reported case. Type-aware receiver resolution is a separate follow-up. |
| 2 | **Re-declared fields / duplicates.** A field assigned in many files (`receiver.field` set repeatedly) yields multiple targets. | `Go-to` shows a chooser; quick-doc must pick one. | Prefer the declaration carrying a `---@type`/`---@field`/doc comment; otherwise first by file order. De-dupe by element. |
| 3 | **Index size.** Indexing every dotted assignment could be large in big projects. | Indexing/memory cost. | Scalar (key-only) index; key only dotted LHS assignments (skip locals). Profile against the `rocks` project. |
| 4 | **Stdlib stub availability in tests.** The runtime stub isn't auto-seeded in unit fixtures; the probe showed the global receiver infers `undefined`. | Type-engine route untestable. | Design avoids the type engine: the `FileBasedIndex` indexes the stub file added via `addFileToProject`, so unit tests are deterministic. |
| 5 | **Collision regression.** A future change could reintroduce bare-name field lookups. | `package.path` → wrong `path.*`. | NAV-12-04 test asserts zero `path.lua` results; keep alongside the member-reference regression test. |
| 6 | **Doc owner discovery.** The riding comment for `receiver.field = value` may attach to the statement, not the index expr. | Wrong/empty doc. | Reuse `LuaPsiImplUtil.getCatsComment`/`LuaDocumentationRenderer`'s owner-walk; add a depth-2 fixture with a `---@type` + leading `---` comment. |
