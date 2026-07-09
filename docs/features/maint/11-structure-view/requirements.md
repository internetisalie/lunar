---
id: MAINT-11
title: "MAINT-11: Test Coverage - Structure View"
type: feature
parent_id: MAINT
status: done
vf_icon: ✅
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-11: Test Coverage - Structure View

## Overview
Add unit-test coverage for the Lua Structure Outline panel hierarchy in
`net.internetisalie.lunar.lang.structure`. The panel currently has **zero** tests. This
feature adds `BasePlatformTestCase` tests that build the structure model from a configured
Lua file and assert the produced tree of nodes (labels, icons, children, leaf/branch
classification, sorting). **No production behavior changes** — this is a coverage feature.

## Scope
* **In Scope**:
  * `LuaStructureViewFactory.getStructureViewBuilder` produces a builder whose model is a
    `LuaStructureViewModel`.
  * `LuaStructureViewModel`: root element type, sorters (`Sorter.ALPHA_SORTER`),
    `isAlwaysLeaf` classification, `getSuitableClasses`.
  * `LuaFileStructureViewTreeElement`: presentable text (file name), file-level children
    from top-level statements (global funcs, local funcs, local var decls, labels, return).
  * `LuaFunctionStructureViewTreeElement` / `LuaLocalFunctionStructureViewTreeElement`:
    presentable text (function name) and nested parameter + block children.
  * `LuaFunctionParameterStructureViewTreeElement`, `LuaLocalVariableStructureViewTreeElement`,
    `LuaReturnStructureViewTreeElement`, `LuaLabelStructureViewTreeElement`: presentable
    text, icon, and empty children (leaf).
  * `TreeElementUtils` routing: `getRootChildren`, `getFuncBodyChildren`, and the per-PSI
    `when` mapping (including the `else -> emptyList()` fallthrough for unhandled statements).
* **Out of Scope**:
  * Testing the Swing structure tool-window UI, navigation side effects (`navigate()`),
    or `PsiNavigationSupport` behavior.
  * Any change to production code under `lang/structure/`.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-11-01 | **Factory & Model wiring** | Must | planned | `LuaStructureViewFactory.getStructureViewBuilder(file)` returns a builder whose `createStructureViewModel(null)` is a `LuaStructureViewModel`; the model's `getRoot()` is a `LuaFileStructureViewTreeElement`, `getSorters()` contains `Sorter.ALPHA_SORTER`, and `getSuitableClasses()` equals the declared `SUITABLE_CLASSES`. |
| MAINT-11-02 | **File-level outline mapping** | Must | planned | `LuaFileStructureViewTreeElement` presents the file name and lists exactly the top-level structural nodes (global function, local function, local variable, label, return) mapped from the file's block. |
| MAINT-11-03 | **Nested function trees** | Must | planned | Global (`LuaFunctionStructureViewTreeElement`) and local (`LuaLocalFunctionStructureViewTreeElement`) function nodes present the function name and contain parameter nodes followed by inner block-statement nodes. |
| MAINT-11-04 | **Leaf nodes & presentation** | Must | planned | Parameter, local-variable, return, and label nodes are leaves (empty children), carry the correct presentable text/icon, and are classified `isAlwaysLeaf == true` by the model. |
| MAINT-11-05 | **Routing utilities** | Must | planned | `TreeElementUtils.getRootChildren` / `getFuncBodyChildren` route each supported PSI statement to its node subclass and drop unsupported statements (e.g. `if`, `while`) via the `else` branch without exception. |

## Test Cases

| TC | Requirement | Given (fixture Lua) | When | Then |
|---|---|---|---|---|
| TC-11-01 | MAINT-11-01 | `x = 1` | `LuaStructureViewFactory().getStructureViewBuilder(file)` then `createStructureViewModel(null)` | Result is a `TreeBasedStructureViewBuilder`; model is `LuaStructureViewModel`. |
| TC-11-02 | MAINT-11-01 | any file | build `LuaStructureViewModel(file)` | `getRoot()` is `LuaFileStructureViewTreeElement`; `getSorters()` contains `Sorter.ALPHA_SORTER`; `getSuitableClasses()` contains `LuaFile`, `LuaLabel`, `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaFinalStatement` (5 entries). |
| TC-11-03 | MAINT-11-02 | `function foo() end`<br>`local function bar() end`<br>`local v = 1`<br>`::done::`<br>`return 1` | `LuaFileStructureViewTreeElement(file).getChildren()` | 5 children whose runtime types are, in source order, `LuaFunctionStructureViewTreeElement`, `LuaLocalFunctionStructureViewTreeElement`, `LuaLocalVariableStructureViewTreeElement`, `LuaLabelStructureViewTreeElement`, `LuaReturnStructureViewTreeElement`. |
| TC-11-04 | MAINT-11-02 | `test.lua` with `x = 1` | `LuaFileStructureViewTreeElement(file).getPresentation().getPresentableText()` | equals `"test.lua"`. |
| TC-11-05 | MAINT-11-02 | `local a, b = 1, 2` | root children | 2 `LuaLocalVariableStructureViewTreeElement` with presentable text `"a"` and `"b"`. |
| TC-11-06 | MAINT-11-03 | `function foo(p, q) local z = 1 end` | `getChildren()` of the `LuaFunctionStructureViewTreeElement` | 3 children: `LuaFunctionParameterStructureViewTreeElement("p")`, `LuaFunctionParameterStructureViewTreeElement("q")`, then `LuaLocalVariableStructureViewTreeElement("z")` (params before block children). |
| TC-11-07 | MAINT-11-03 | `function foo() end` | function node presentation | presentable text equals `"foo"`; icon is `AllIcons.Nodes.Function`. |
| TC-11-08 | MAINT-11-03 | `local function bar(n) return n end` | local-function node children | `LuaFunctionParameterStructureViewTreeElement("n")` then `LuaReturnStructureViewTreeElement`; presentable text of the node equals `"bar"`. |
| TC-11-09 | MAINT-11-04 | `::top::` at file scope | label node | presentable text equals `"top"`; icon `AllIcons.Nodes.Bookmark`; `getChildren()` empty. |
| TC-11-10 | MAINT-11-04 | `return true` at file scope | return node | presentable text equals `"return"`; icon `AllIcons.Debugger.EvaluationResult`; `getChildren()` empty. |
| TC-11-11 | MAINT-11-04 | `local v = 1` | local-variable node | presentable text `"v"`; icon `AllIcons.Nodes.Variable`; children empty. |
| TC-11-12 | MAINT-11-04 | any of local-var / label / return nodes | `LuaStructureViewModel.isAlwaysLeaf(node)` | returns `true`; for a `LuaFunctionStructureViewTreeElement` returns `false`. |
| TC-11-13 | MAINT-11-05 | `if true then end`<br>`while true do end`<br>`x = 1` | `TreeElementUtils.getRootChildren(file)` | empty list (all unsupported top-level statements routed to `else`), no exception. |
| TC-11-14 | MAINT-11-05 | function with `parList = p` and a block containing `local q = 1` | `TreeElementUtils.getFuncBodyChildren(parList, block)` | 2 elements: parameter node for `p`, local-variable node for `q`. |
| TC-11-15 | MAINT-11-05 | `function foo() end` where funcName is nil-par | `TreeElementUtils.getFuncBodyChildren(funcDecl.parList, funcDecl.block)` with no params | returns only block children (empty for empty body); no NPE on null/empty parameter list. |

## Acceptance Criteria
* **AC-11-01**: TC-11-01, TC-11-02 pass — factory/model wiring verified.
* **AC-11-02**: TC-11-03, TC-11-04, TC-11-05 pass — file outline mapping verified.
* **AC-11-03**: TC-11-06, TC-11-07, TC-11-08 pass — nested function trees verified.
* **AC-11-04**: TC-11-09, TC-11-10, TC-11-11, TC-11-12 pass — leaf presentation verified.
* **AC-11-05**: TC-11-13, TC-11-14, TC-11-15 pass — routing verified including `else` fallthrough.
