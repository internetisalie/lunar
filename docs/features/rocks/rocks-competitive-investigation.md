---
id: "ROCKS-COMPETITIVE"
title: "Competitive Investigation"
type: "spec"
parent_id: "ROCKS"
priority: "high"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# Competitive Investigation: Remote Repository Management

This document summarizes how other JetBrains plugins and built-in features handle remote repository management, search, and dependency visualization. These serve as reference implementations for the **ROCKS** integration.

## 1. Reference Implementations

### Python / PyPI (PyCharm)
- **Component**: "Python Packages" tool window.
- **Key Features**:
    - Visual browser for PyPI.
    - Displays installed version vs. latest available.
    - Supports version pinning and multiple package sources (custom repositories).
- **Relevance**: Best-in-class example for managing "Global" vs "Project" trees and virtual environments.

### JavaScript / npm (Built-in)
- **Component**: "Packages" tab in the tool window.
- **Key Features**:
    - Split view: Search results on the left, README/Metadata on the right.
    - Handles `.npmrc` authentication and registry switching.
    - Integrated install/uninstall actions.
- **Relevance**: Direct analog for the "Package Browser" component.

### Maven & Gradle (Built-in)
- **Component**: "Dependency Analyzer" and search indices.
- **Key Features**:
    - **Hierarchical Tree View**: Shows deep transitive dependencies and identifies version conflicts.
    - **Search Indices**: Local indexing of remote repositories for instant searching.
- **Relevance**: Reference for the **Hierarchical Dependency Tree View** and conflict resolution.

### Docker Plugin (Bundled)
- **Component**: "Services" tool window.
- **Key Features**:
    - Connects to multiple remote registries (Docker Hub, AWS ECR, GitLab, etc.).
    - Tree view for browsing tags and metadata.
- **Relevance**: Example of managing multiple discrete remote servers and metadata inspection.

## 2. Design Recommendations for ROCKS

Based on these competitive benchmarks, the ROCKS feature should adopt the following patterns:

| Feature | Recommended UI Pattern | Reference |
| :--- | :--- | :--- |
| **Search UI** | Split view (Search Results \| Documentation) | npm / PyPI |
| **Dependency View** | Hierarchical Tree with conflict markers | Maven / Gradle |
| **Registry Management** | Configurable list of remote manifests | Docker / npm |
| **Versioning** | Dropdown with remote versions + "Latest" badge | PyPI |

## 3. Implementation Targets
- **ROCKS-02**: Utilize the split-view pattern for the Package Browser.
- **ROCKS-03**: Implement the hierarchical tree view for complex dependency resolution.
