---
id: "RUN-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "RUN-03"
status: "planned"
priority: "low"
folders:
  - "[[features/debug/03-interactive-console/requirements|requirements]]"
---

# Risks & Design Gaps: RUN-03 Interactive Console (REPL)

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `RUN-03-R-01` | **Incomplete-detection accuracy** | Medium | The EOF-error trial-parse heuristic (§3.1) is checked by TC-RUN-03-01/02/03; a blank-line force-submit is the escape hatch. |
| `RUN-03-R-02` | **`lua -i` prompt echo** | Medium | `lua -i` prints its own `> ` prompts; the console may need to suppress/absorb them — validated in `RUN-03-DR-02`. |
| `RUN-03-R-03` | **Output buffering** | Low | `setvbuf('no')` forces unbuffered output (RUN-03-08); some builds may still buffer pipes. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `RUN-03-G-01` | **Detection fallback** | Whether to fall back to a `load`/`<eof>` bootstrap if the trial parse mis-detects. | `RUN-03-DR-01` |
| `RUN-03-G-02` | **Session-aware completion** | RUN-03-06 session symbols (vars defined in the REPL) are not in the PSI. | `RUN-03-DR-03` |

## De-risking Tasks (DR)

- [ ] `RUN-03-DR-01`: If the trial-parse heuristic mis-detects in practice, add a `load`/`<eof>`
      bootstrap as the authority.
- [ ] `RUN-03-DR-02`: Validate `lua -i` prompt/echo behaviour across interpreters; suppress the
      native prompt or run non-interactive with a bootstrap loop.
- [ ] `RUN-03-DR-03`: Decide how to surface REPL-session-defined symbols in completion (query the
      live interpreter, or skip — Should/Could).
