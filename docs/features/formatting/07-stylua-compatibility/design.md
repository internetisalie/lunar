---
id: FORMAT-07-DESIGN
title: Stylua Compatibility Design
type: design
parent_id: FORMAT-07
status: planned
---

# Technical Design: Stylua Compatibility

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.formatting.external.StyluaFormattingService`
- **Implements**: `com.intellij.formatting.service.AsyncDocumentFormattingService`

## 2. Core Algorithms
1. Implement `AsyncDocumentFormattingService`.
2. Check if Stylua is enabled in settings.
3. Pipe the document text to the `stylua` executable via CLI, read stdout, and apply the formatted string as a text replacement.

## 3. Integration Points
```xml
<documentFormattingService implementation="net.internetisalie.lunar.lang.formatting.external.StyluaFormattingService"/>
```
