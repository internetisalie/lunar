---
id: MAINT-16
title: "MAINT-16: Test Coverage - LuaCATS Syntax & Highlighting"
type: feature
parent_id: MAINT
status: todo
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-16: Test Coverage - LuaCATS Syntax & Highlighting

## Overview
Increase test coverage for LuaCATS type annotation lexing, parsing, semantic code highlighting, and Quick Documentation HTML page rendering.

## Scope
* **In Scope**:
  * Unit tests for lazy parsing and tag lookup queries in `LuaCatsLazyCommentImpl`.
  * Unit tests for tokenizer states in `LuaCatsLexer` and `LuaCatsSyntaxHighlighter`.
  * Unit tests for annotator highlighting rules (like type names vs local fields) in `LuaCatsAnnotator`.
  * Unit tests for Quick Documentation HTML composition (such as parameters, return values, and tags formatting) in `LuaCatsDocumentationRenderer`.
* **Out of Scope**:
  * Testing editor fonts and theme colors.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-16-01 | **Lazy Comment Tag Queries** | Must | planned | Verify that `LuaCatsLazyCommentImpl` lazy-parses tags and returns fields like `@param`, `@class`, and `@return` on demand. |
| MAINT-16-02 | **Tokenization & Highlights** | Must | planned | Verify that `LuaCatsLexer` splits tags, keywords, type names, and string literals correctly. |
| MAINT-16-03 | **Semantic Type Annotations** | Must | planned | Verify that `LuaCatsAnnotator` applies correct highlights to types, deprecated tags, and alias identifiers. |
| MAINT-16-04 | **Quick Documentation Renderer** | Must | planned | Verify that `LuaCatsDocumentationRenderer` produces structured HTML blocks with description text, links, and parameters. |

## Acceptance Criteria
* **AC-16-01**: A test case asserts that `catsComment.getClassTag()` returns the correct class name for `---@class Builder`.
* **AC-16-02**: A test case asserts that tokenizing `---@param name string` returns a sequence of tokens containing the tag, parameter name, and type key.
* **AC-16-03**: A test case asserts that `LuaCatsAnnotator` flags deprecated tags with the strikeout highlight attribute.
* **AC-16-04**: A test case asserts that invoking documentation on a LuaCATS type yields an HTML block containing the type description and parameter list.
