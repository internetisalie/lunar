---
id: DOC-06-DESIGN
title: Documentation Indexing Design
type: design
parent_id: DOC-06
status: planned
---

# Technical Design: Documentation Indexing

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.indexing.LuaDocIndex`
- **Implements**: `com.intellij.util.indexing.ScalarIndexExtension`

## 2. Core Algorithms
1. Create a `FileBasedIndexExtension` that maps a symbol name (or file path + offset) to the serialized text of its LuaDoc block.
2. In the `QuickDocumentationProvider`, query this index first.
