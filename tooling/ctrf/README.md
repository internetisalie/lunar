# Unit-test results per commit (MAINT-48)

`junit_ctrf.py` turns a `test` run's JUnit XML (`build/test-results/test/`) and the test-set
sidecar the task writes beside it (`build/test-results/test-filter.json`) into one CTRF 0.2.0
document, stores it in loom's conformance store as check `a3` (no suite), fetches a commit's
record, and compares two documents test by test. `loom_store.py` holds the `loom-cli` calls it
shares with `tooling/corpus/corpus_ctrf.py`. Stdlib only.

- **`assemble`**: `python3 tooling/ctrf/junit_ctrf.py assemble --results build/test-results/test --filter build/test-results/test-filter.json --out unit.ctrf.json`. Exit `2` on a missing or unknown sidecar, no XML, or a duplicate `testId`.
- **`store`**: `python3 tooling/ctrf/junit_ctrf.py store --results build/test-results/test --filter build/test-results/test-filter.json --out build/unit-ctrf/unit.ctrf.json [--repo .]`. Puts against `HEAD` unless a record exists (`0` either way); a `--tests` run is refused with `2`; a `loom-cli` failure exits `1`.
- **`fetch`**: `python3 tooling/ctrf/junit_ctrf.py fetch --commit <sha> --out before.ctrf.json [--max-depth N] [--repo .]`. The record of the nearest first-parent ancestor whose code equals `<sha>`'s; `3` when none. A local shell needs the store credentials loaded (`set -a; . ~/.config/loom/loom.artifacts.env; set +a`).
- **`diff`**: `python3 tooling/ctrf/junit_ctrf.py diff before.ctrf.json after.ctrf.json`. Prints `NEWLY_FAILING`, `NEWLY_SKIPPED`, `NEWLY_PASSING`, `STATUS`, `ADDED`, `REMOVED` and `OUT_OF_SET` lines, then `EXECUTED <before> <after>` (and `EXECUTED_DROP <n>`). Exit `1` on a new failure or a passed test now skipped, `4` on an executed-count drop, `2` when not comparable.

Tests: `cd tooling/ctrf && python3 -m unittest test_junit_ctrf`.

# Performance records per commit (MAINT-45)

`perf_ctrf.py` turns the counted-workload records a `-PwithPerfRecords` run writes (`build/perf-records/*.json`) and `/proc/cpuinfo` into one CTRF 0.2.0 document, and stores it from `main` as check `a3` suite `perf`. Stdlib only; it reuses `loom_store.py`.

- **`assemble`**: `python3 tooling/ctrf/perf_ctrf.py assemble --records build/perf-records --out perf.ctrf.json [--cpuinfo /proc/cpuinfo]`. Exit `2` on no records or a record of another schema.
- **`ci`**: `python3 tooling/ctrf/perf_ctrf.py ci --records build/perf-records --out build/perf-ctrf/perf.ctrf.json --accepted tooling/perf/accepted-increases.json --gate-mode tooling/perf/gate-mode --event "$GITHUB_EVENT_NAME" [--base-ref R] [--repo .]`. Prints `perf: gate-mode <mode>` and a `WOBBLE` line for each value that differs between a workload's two passes. A `push` puts the document against `HEAD` unless it has a record; any other event stores nothing. `tooling/perf/gate-mode` holds `report-only` (a wobble prints and cannot fail) or `gating` (a wobble fails, and a push does not store). Exit `2` on a missing or unknown gate-mode file.

Tests: `cd tooling/ctrf && python3 -m unittest test_perf_ctrf`.
