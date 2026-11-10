#!/usr/bin/env python3
"""MAINT-56: the representative type corpus — its domains, its members and their checks.

``type-domains.json`` holds the host domains and their measured public usage (design §2.0);
``type-corpus.json`` the members, stratified by those domains (design §2.1). Everything else is in
``docs/features/maint/56-representative-type-corpus/design.md``.

Subcommands (design §4.2):
  check  [--manifest P] [--sweep P] [--domains P] [--checkouts DIR]   exit 0 ok, 1 on any error
  usage  [--domains P]                                                re-measure counts with `gh`; exit 2 on failure
  summarize <run-dir> --checkouts DIR --lunar-commit SHA --out FILE [--manifest P] [--domains P]
         write rule-weights.json (design §4.3), print the readings of design §3.7; exit 2 on a bad run

Run without ``python3 -I``: the script imports its sibling ``type_audit_summary`` (risks-and-gaps DR-10).
"""
from __future__ import annotations

import argparse
import copy
import datetime
import json
import re
import subprocess
import sys
import time
from pathlib import Path

from type_audit_summary import F_THRESHOLD, R_THRESHOLD, aggregate, rated, verdict

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
BUG_MAP = SCRIPT_DIR / "type_bug_map.json"
MANIFEST = SCRIPT_DIR / "type-corpus.json"
SWEEP_MANIFEST = SCRIPT_DIR / "corpus.json"
DOMAINS = SCRIPT_DIR / "type-domains.json"

COHORTS = ("young", "small", "established")
REQUIRED = ("domain", "cohort", "licence")
NAMED_HOSTS = {"neovim", "redis", "openresty", "tarantool", "wow", "factorio", "love", "defold"}
SAMPLING_SHARE = 0.02
MIN_MEMBERS = 3
LINE_CAP = 3500
NAME = re.compile(r"[a-z]+")
CATS_TAG = re.compile(r"^\s*---\s*@(param|return|class|field|type|alias|generic|overload|enum|cast)\b")
SEARCH_PAUSE_SECONDS = 7
SCHEMA = 1
FAMILIES = ("arity.tooMany", "arity.tooFew", "field.missing", "return.mismatch", "assign.other")
OWN_FAMILY_SHAPES = ("arity.tooMany", "arity.tooFew", "return.mismatch")


class UsageError(Exception):
    """A usage measurement failed; carries the `gh` stderr."""


class BadRun(Exception):
    """A run directory that cannot be summarised: a payload missing, at the wrong commit or schema."""


def load(path) -> list[dict]:
    return json.loads(Path(path).read_text(encoding="utf-8"))["corpora"]


def load_domains(path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def host_count(host: dict) -> int:
    return max(host["counts"].values())


def domain_count(domain: dict) -> int:
    return sum(host_count(host) for host in domain["hosts"])


def weighted_domains(domains: dict) -> list[dict]:
    return [domain for domain in domains["domains"] if domain["sampled"] and domain["weighted"]]


def weights_of(domains: dict) -> dict[str, float]:
    """A sampled, weighted domain's count over the sum of all such domains' counts (design §2.0)."""
    counted = {domain["id"]: domain_count(domain) for domain in weighted_domains(domains)}
    total = sum(counted.values())
    return {name: count / total for name, count in counted.items()}


def measured_total(domains: dict) -> int:
    """Every entry of `domains`; `excluded` is never in the sum."""
    return sum(domain_count(domain) for domain in domains["domains"])


def coverage(domains: dict) -> float:
    return sum(domain_count(domain) for domain in weighted_domains(domains)) / measured_total(domains)


def share_crossings(domains: dict) -> list[str]:
    """Hosts outside NAMED_HOSTS whose measured share disagrees with their frozen `sampled` value."""
    total = measured_total(domains)
    lines = []
    for domain in domains["domains"]:
        if domain["kind"] != "host" or domain["id"] in NAMED_HOSTS:
            continue
        share = domain_count(domain) / total
        if domain["sampled"] != (share >= SAMPLING_SHARE):
            lines.append(
                f"{domain['id']}: {share * 100:.2f} % of measured usage crosses the 2 % sampling line; "
                f"sampled stays {'true' if domain['sampled'] else 'false'}"
            )
    return lines


def sampled_ids(domains: dict) -> list[str]:
    return [domain["id"] for domain in domains["domains"] if domain["sampled"]]


def density(checkout: Path, roots: list[str]) -> dict:
    """Design §3.1: non-blank lines and LuaCATS tag lines over every `*.lua` under the roots."""
    result = {"files": 0, "lines": 0, "catsTags": 0, "maxFileLines": 0, "maxFile": None}
    for root in roots:
        for path in sorted((Path(checkout) / root).rglob("*.lua")):
            text = path.read_text(encoding="utf-8", errors="replace").splitlines()
            lines = sum(1 for line in text if line.strip())
            result["files"] += 1
            result["lines"] += lines
            result["catsTags"] += sum(1 for line in text if CATS_TAG.match(line))
            if lines > result["maxFileLines"]:
                result["maxFileLines"] = lines
                result["maxFile"] = path.relative_to(checkout).as_posix()
    return result


def entry_errors(entry: dict, sampled: list[str], seen: set, sweep_names: set) -> list[str]:
    name = entry.get("name", "?")
    errors = [f"{name}: missing '{field}'" for field in REQUIRED if not entry.get(field)]
    if entry.get("domain") and entry["domain"] not in sampled:
        errors.append(f"{name}: unknown domain '{entry['domain']}'")
    if entry.get("cohort") and entry["cohort"] not in COHORTS:
        errors.append(f"{name}: unknown cohort '{entry['cohort']}'")
    if not NAME.fullmatch(name):
        errors.append(f"{name}: name must be [a-z]+")
    if name in seen:
        errors.append(f"{name}: duplicate")
    if name in sweep_names:
        errors.append(f"{name}: also in corpus.json")
    seen.add(name)
    return errors


def cap_error(entry: dict, checkouts: Path) -> list[str]:
    measured = density(Path(checkouts) / entry["name"], entry["roots"])
    if measured["maxFileLines"] <= LINE_CAP:
        return []
    return [f"{entry['name']}: {measured['maxFile']} has {measured['maxFileLines']} lines, cap {LINE_CAP}"]


def check(entries: list[dict], sweep_names: set, domains: dict, checkouts=None) -> list[str]:
    """Design §4.2: per entry in manifest order, then per sampled domain."""
    sampled = sampled_ids(domains)
    seen: set = set()
    errors = []
    for entry in entries:
        errors += entry_errors(entry, sampled, seen, sweep_names)
        if checkouts is not None:
            errors += cap_error(entry, checkouts)
    for domain in sampled:
        members = sum(1 for entry in entries if entry.get("domain") == domain)
        if members < MIN_MEMBERS:
            errors.append(f"{domain}: {members} members, need {MIN_MEMBERS}")
    return errors


def measure_usage(domains: dict, search, today: str) -> dict:
    """Every count in `domains` and `excluded` replaced by `search` of the domain file's query."""
    measured = copy.deepcopy(domains)
    counted = [counts for domain in measured["domains"] for counts in (host["counts"] for host in domain["hosts"])]
    counted += [entry["counts"] for entry in measured.get("excluded", [])]
    for counts in counted:
        for signature in counts:
            phrase = signature.replace('"', '\\"')
            counts[signature] = search(measured["query"].replace("<signature>", phrase))
    measured["measuredAt"] = today
    return measured


def gh_search(query: str) -> int:
    """GitHub code search's total_count for `query`; code search allows 10 requests a minute."""
    completed = subprocess.run(
        ["gh", "api", "-X", "GET", "search/code", "-f", f"q={query}", "-f", "per_page=1", "--jq", ".total_count"],
        capture_output=True,
        text=True,
    )
    time.sleep(SEARCH_PAUSE_SECONDS)
    if completed.returncode != 0:
        raise UsageError(completed.stderr.strip())
    return int(completed.stdout.strip())


def payload_file(run_dir: Path, sub: str, name: str, commit: str) -> dict:
    path = Path(run_dir) / sub / f"{name}.json"
    if not path.is_file():
        raise BadRun(f"missing payload: {sub}/{name}.json")
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schema") != SCHEMA:
        raise BadRun(f"{sub}/{name}.json: schema {payload.get('schema')} is not {SCHEMA}")
    if payload.get("commit") != commit:
        raise BadRun(f"{sub}/{name}.json: commit {payload.get('commit')} is not the manifest's {commit}")
    return payload


def load_run(run_dir: Path, entries: list[dict], checkouts: Path) -> list[dict]:
    """Design §5.1: one record per manifest entry, its payloads and its density."""
    members = []
    for entry in entries:
        name, commit = entry["name"], entry["commit"]
        measured = density(Path(checkouts) / name, entry["roots"])
        members.append({
            "name": name,
            "domain": entry["domain"],
            "kloc": measured["lines"] / 1000,
            "catsTags": measured["catsTags"],
            "audit": payload_file(run_dir, "audit", name, commit),
            "extras": payload_file(run_dir, "extras", name, commit),
        })
    return members


def by_domain(members: list[dict]) -> dict[str, list[dict]]:
    grouped: dict[str, list[dict]] = {}
    for member in members:
        grouped.setdefault(member["domain"], []).append(member)
    return grouped


def all_shapes(members: list[dict]) -> list[str]:
    return sorted({key[0] for key in aggregate([member["audit"] for member in members])["cells"]})


def population(member: dict, shape: str) -> int:
    return sum(cell["population"] for cell in member["audit"]["cells"] if cell["cell"]["shape"] == shape)


def exposure(members: list[dict], w: dict) -> dict[str, dict[str, float]]:
    """Design §3.3: per shape and domain, the mean over the domain's members of sites per kloc.

    Every domain with members is present, weighted or not; `w` is applied by `weights` when pooling.
    """
    grouped = by_domain(members)
    return {
        shape: {
            domain: sum(population(member, shape) / member["kloc"] for member in group) / len(group)
            for domain, group in grouped.items()
        }
        for shape in all_shapes(members)
    }


def silent_rates(cells: dict) -> dict[str, float]:
    """Design §3.4: per shape, Σ population·rate / Σ population over the domain's rated cells."""
    totals: dict[str, list[float]] = {}
    for key, rate in rated(cells).items():
        total = totals.setdefault(key[0], [0.0, 0.0])
        total[0] += cells[key]["population"] * rate
        total[1] += cells[key]["population"]
    return {shape: silent / pop for shape, (silent, pop) in totals.items() if pop}


def domain_cells(group: list[dict], drop_library: bool) -> dict:
    cells = aggregate([member["audit"] for member in group])["cells"]
    return {key: row for key, row in cells.items() if not (drop_library and key[1] == "LIBRARY")}


def weighted_mean(values: dict[str, float], w: dict) -> float:
    """Σ w_d·v_d / Σ w_d over the weighted domains in `values`; 0.0 when there are none."""
    present = [domain for domain in values if domain in w]
    total = sum(w[domain] for domain in present)
    return sum(w[domain] * values[domain] for domain in present) / total if total else 0.0


def plain_mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def judged(f_value: float, r_value: float) -> dict:
    reopen = f_value >= F_THRESHOLD and r_value >= R_THRESHOLD
    return {"F": f_value, "R": r_value, "verdict": "REOPEN" if reopen else "HOLD"}


def domain_verdicts(members: list[dict], w: dict, drop_library: bool) -> dict:
    """Design §3.4: each domain's MAINT-54 verdict, the weighted headline, the domain mean and the pooled verdict."""
    per_domain = {
        domain: verdict(domain_cells(group, drop_library)) for domain, group in by_domain(members).items()
    }
    with_p = {domain: result["F"] for domain, result in per_domain.items() if result["P"] > 0}
    with_s = {domain: result["R"] for domain, result in per_domain.items() if result["S"] > 0}
    pooled_cells = domain_cells(members, drop_library)
    return {
        "domains": per_domain,
        "weighted": judged(weighted_mean(with_p, w), weighted_mean(with_s, w)),
        "domainMean": judged(plain_mean(list(with_p.values())), plain_mean(list(with_s.values()))),
        "pooled": judged(verdict(pooled_cells)["F"], verdict(pooled_cells)["R"]),
    }


def false_positives(members: list[dict], w: dict) -> dict:
    """Design §3.5: per family and domain, the mean of each member's own findings per kloc; pooled by weight."""
    result = {}
    for family in FAMILIES:
        per_domain = {
            domain: plain_mean([member["extras"]["baseline"].get(family, 0) / member["kloc"] for member in group])
            for domain, group in by_domain(members).items()
        }
        pooled = sum(w[domain] * value for domain, value in per_domain.items() if domain in w)
        result[family] = {"byDomain": per_domain, "pooled": pooled}
    return result


def matches(value: str, allowed) -> bool:
    return allowed == "*" or value in allowed


def bug_for_cell(cell, rules: dict) -> str:
    """Design §3.6: the first rule matching (shape, path, form), else `unmapped`."""
    shape, path, form = cell
    for rule in rules["cells"]:
        if shape in rule.get("exceptShapes", []):
            continue
        if matches(shape, rule["shapes"]) and matches(path, rule["paths"]) and matches(form, rule["forms"]):
            return rule["bug"]
    return "unmapped"


def bug_rules(members: list[dict], rules: dict) -> dict:
    """Every silent cell of every domain, by bug: the sorted distinct shapes; `unmapped` lists the cells."""
    shapes: dict[str, set] = {}
    unmapped = []
    for domain, group in sorted(by_domain(members).items()):
        for key, rate in sorted(rated(domain_cells(group, False)).items()):
            if rate <= 0:
                continue
            bug = bug_for_cell(key, rules)
            if bug == "unmapped":
                unmapped.append({"shape": key[0], "path": key[1], "form": key[2], "domain": domain})
            else:
                shapes.setdefault(bug, set()).add(key[0])
    return {**{bug: sorted(found) for bug, found in shapes.items()}, "unmapped": unmapped}


def family_of(shape: str) -> str:
    return shape if shape in OWN_FAMILY_SHAPES else "assign.other"


def missed(exposure_value: float, rate):
    return None if rate is None else exposure_value * rate


def shape_rows(members: list[dict], w: dict) -> dict:
    """Design §3.4's per-shape figures: by domain, and pooled over the weighted domains only."""
    exposures = exposure(members, w)
    rates = {domain: silent_rates(domain_cells(group, False)) for domain, group in by_domain(members).items()}
    result = {}
    for shape, per_domain in exposures.items():
        by = {
            domain: {"exposurePerKloc": value, "silentRate": rates[domain].get(shape),
                     "missedPerKloc": missed(value, rates[domain].get(shape))}
            for domain, value in per_domain.items()
        }
        result[shape] = {"family": family_of(shape), "byDomain": by, "pooled": pooled_shape(by, w)}
    return result


def pooled_shape(by: dict, w: dict) -> dict:
    rated_domains = {domain: row for domain, row in by.items() if domain in w and row["silentRate"] is not None}
    pooled_exposure = sum(w[domain] * row["exposurePerKloc"] for domain, row in by.items() if domain in w)
    if not rated_domains:
        return {"exposurePerKloc": pooled_exposure, "silentRate": None, "missedPerKloc": None}
    return {
        "exposurePerKloc": pooled_exposure,
        "silentRate": weighted_mean({d: row["silentRate"] for d, row in rated_domains.items()}, w),
        "missedPerKloc": weighted_mean({d: row["missedPerKloc"] for d, row in rated_domains.items()}, w),
    }


def domain_rows(members: list[dict], w: dict, verdicts: dict) -> dict:
    result = {}
    for domain, group in by_domain(members).items():
        kloc = sum(member["kloc"] for member in group)
        judged_domain = verdicts["domains"][domain]
        result[domain] = {
            "members": sorted(member["name"] for member in group),
            "weight": w.get(domain),
            "kloc": kloc,
            "catsPerKloc": sum(member["catsTags"] for member in group) / kloc if kloc else None,
            "F": judged_domain["F"],
            "R": judged_domain["R"],
            "verdict": judged_domain["verdict"],
        }
    return result


def rounded(value):
    if isinstance(value, float):
        return round(value, 4)
    if isinstance(value, dict):
        return {key: rounded(item) for key, item in value.items()}
    if isinstance(value, list):
        return [rounded(item) for item in value]
    return value


def timed_out(members: list[dict]) -> list[str]:
    """Members whose run passed the per-member deadline (design §5.2); they enter no figure."""
    return sorted(member["name"] for member in members if member["extras"].get("timedOut"))


def weights(members: list[dict], domains: dict, rules: dict, meta: dict) -> dict:
    """Design §4.3: the rule-weights document; `meta` holds `manifest`, `domains` and `lunarCommit`."""
    w = weights_of(domains)
    corpus = {"manifest": meta["manifest"], "members": {m["name"]: m["audit"]["commit"] for m in members}}
    stopped = timed_out(members)
    members = [member for member in members if member["name"] not in stopped]
    verdicts = domain_verdicts(members, w, drop_library=False)
    stratum = domain_verdicts(members, w, drop_library=True)
    return rounded({
        "schema": SCHEMA,
        "corpus": corpus,
        "timedOut": stopped,
        "lunarCommit": meta["lunarCommit"],
        "thresholds": {"F": F_THRESHOLD, "R": R_THRESHOLD},
        "usage": {"domains": meta["domains"], "measuredAt": domains["measuredAt"], "coverage": coverage(domains),
                  "crossings": share_crossings(domains)},
        "domains": domain_rows(members, w, verdicts),
        "verdicts": {"weighted": verdicts["weighted"], "domainMean": verdicts["domainMean"],
                     "pooled": verdicts["pooled"], "nonLibrary": stratum["weighted"]},
        "shapes": shape_rows(members, w),
        "families": {family: {"byDomain": {d: {"findingsPerKloc": v} for d, v in row["byDomain"].items()},
                              "pooled": {"findingsPerKloc": row["pooled"]}}
                     for family, row in false_positives(members, w).items()},
        "bugs": bug_rules(members, rules),
    })


def shown(value, digits: int = 2) -> str:
    """A figure with no measured site is `-`, never 0 (requirements, Behavior Rules)."""
    return "-" if value is None else f"{value:.{digits}f}"


def catch(rate) -> str:
    return "-" if rate is None else f"{1 - rate:.2f}"


def shape_order(shapes: dict) -> list[str]:
    """Pooled missed exposure descending, then shape name; shapes with no pooled `M` after, by name."""
    measured_shapes = [shape for shape, row in shapes.items() if row["pooled"]["missedPerKloc"] is not None]
    unmeasured = sorted(shape for shape in shapes if shape not in measured_shapes)
    return sorted(measured_shapes, key=lambda shape: (-shapes[shape]["pooled"]["missedPerKloc"], shape)) + unmeasured


def figure_row(label: str, row: dict, findings) -> str:
    return (f"| {label} | {shown(row['exposurePerKloc'])} | {catch(row['silentRate'])} | "
            f"{shown(row['missedPerKloc'])} | {shown(findings)} |")


def reading_rules(doc: dict) -> list[str]:
    lines = ["## Reading 1: rule weights", "",
             "| shape / domain | exposure per kloc | catch rate | missed per kloc | family findings per kloc |",
             "|---|---|---|---|---|"]
    for shape in shape_order(doc["shapes"]):
        row, family = doc["shapes"][shape], doc["families"][doc["shapes"][shape]["family"]]
        lines.append(figure_row(f"**{shape}** ({row['family']}, pooled)", row["pooled"],
                                family["pooled"]["findingsPerKloc"]))
        for domain, figures in sorted(row["byDomain"].items()):
            lines.append(figure_row(f"{shape} / {domain}", figures,
                                    family["byDomain"].get(domain, {}).get("findingsPerKloc")))
    lines += ["", "### Bug → rule", ""]
    lines += [f"- {bug}: {', '.join(shapes)}" for bug, shapes in sorted(doc["bugs"].items()) if bug != "unmapped"]
    unmapped = doc["bugs"]["unmapped"]
    lines += [f"- unmapped: {cell['shape']} / {cell['path']} / {cell['form']} ({cell['domain']})" for cell in unmapped]
    return lines + ([] if unmapped else ["- unmapped: none"]) + [""]


def verdict_line(label: str, result: dict) -> str:
    agrees = "agrees" if result["verdict"] == "REOPEN" else "does not agree"
    return (f"- {label}: F = {result['F']:.2f}, R = {result['R']:.2f}, **{result['verdict']}** "
            f"({agrees} with MAINT-54's REOPEN)")


def reading_redesign(doc: dict) -> list[str]:
    verdicts, usage = doc["verdicts"], doc["usage"]
    lines = ["## Reading 2: redesign", "", verdict_line("Weighted (headline)", verdicts["weighted"]), "",
             "| domain | weight | kloc | cats per kloc | F | R | verdict |", "|---|---|---|---|---|---|---|"]
    for domain, row in sorted(doc["domains"].items()):
        weight = "unweighted" if row["weight"] is None else f"{row['weight']:.4f}"
        lines.append(f"| {domain} | {weight} | {row['kloc']:.1f} | {shown(row['catsPerKloc'], 1)} | "
                     f"{row['F']:.2f} | {row['R']:.2f} | {row['verdict']} |")
    lines += ["", verdict_line("Unweighted domain mean", verdicts["domainMean"]),
              verdict_line("Pooled", verdicts["pooled"]),
              verdict_line("Non-LIBRARY stratum (weighted)", verdicts["nonLibrary"]),
              f"- Coverage of measured usage: {usage['coverage'] * 100:.1f} % (measured {usage['measuredAt']})"]
    lines += [f"- {line}" for line in usage["crossings"]] or ["- Sampling-line crossings: none"]
    stopped = ", ".join(doc["timedOut"]) or "none"
    return lines + [f"- Timed out, in no figure above: {stopped}", ""]


def render(doc: dict) -> str:
    """Design §3.7: the two decision readings, as Markdown."""
    header = [f"Lunar `{doc['lunarCommit']}`; thresholds F ≥ {doc['thresholds']['F']}, R ≥ {doc['thresholds']['R']}", ""]
    return "\n".join(header + reading_rules(doc) + reading_redesign(doc))


def display_path(path: Path) -> str:
    resolved = Path(path).resolve()
    return resolved.relative_to(REPO_ROOT).as_posix() if resolved.is_relative_to(REPO_ROOT) else str(path)


def run_summarize(args) -> int:
    try:
        members = load_run(args.run_dir, load(args.manifest), args.checkouts)
    except BadRun as failure:
        print(failure, file=sys.stderr)
        return 2
    meta = {"manifest": display_path(args.manifest), "domains": display_path(args.domains),
            "lunarCommit": args.lunar_commit}
    doc = weights(members, load_domains(args.domains), json.loads(BUG_MAP.read_text(encoding="utf-8")), meta)
    Path(args.out).write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(render(doc), end="")
    return 0


def run_check(args) -> int:
    entries = load(args.manifest)
    domains = load_domains(args.domains)
    errors = check(entries, {entry["name"] for entry in load(args.sweep)}, domains, args.checkouts)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f"type-corpus ok ({len(entries)} members, {len(sampled_ids(domains))} domains)")
    return 0


def run_usage(args) -> int:
    today = datetime.datetime.now(datetime.timezone.utc).date().isoformat()
    try:
        measured = measure_usage(load_domains(args.domains), gh_search, today)
    except UsageError as failure:
        print(f"type_corpus usage: gh failed: {failure}", file=sys.stderr)
        return 2
    Path(args.domains).write_text(json.dumps(measured, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="type_corpus.py", description=__doc__.splitlines()[0])
    commands = root.add_subparsers(dest="command", required=True)
    check_command = commands.add_parser("check", help="validate the manifest against the domains")
    check_command.add_argument("--manifest", type=Path, default=MANIFEST)
    check_command.add_argument("--sweep", type=Path, default=SWEEP_MANIFEST)
    check_command.add_argument("--domains", type=Path, default=DOMAINS)
    check_command.add_argument("--checkouts", type=Path, default=None)
    check_command.set_defaults(run=run_check)
    usage_command = commands.add_parser("usage", help="re-measure the domains' GitHub code-search counts")
    usage_command.add_argument("--domains", type=Path, default=DOMAINS)
    usage_command.set_defaults(run=run_usage)
    summarize_command = commands.add_parser("summarize", help="write the rule weights of a representative run")
    summarize_command.add_argument("run_dir", type=Path)
    summarize_command.add_argument("--checkouts", type=Path, required=True)
    summarize_command.add_argument("--lunar-commit", required=True)
    summarize_command.add_argument("--out", type=Path, required=True)
    summarize_command.add_argument("--manifest", type=Path, default=MANIFEST)
    summarize_command.add_argument("--domains", type=Path, default=DOMAINS)
    summarize_command.set_defaults(run=run_summarize)
    return root


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
