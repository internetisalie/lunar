"""The MAINT-40 design §4.3 stub ``loom-cli``, shared by the corpus and unit-test CTRF tests."""

STUB = """#!/usr/bin/env python3
import json, os, shutil, sys
scenario_path = os.environ["STUB_LOOM"]
argv = sys.argv[1:]
with open(scenario_path + ".calls", "a", encoding="utf-8") as calls:
    calls.write(json.dumps(argv) + "\\n")
scenario = json.load(open(scenario_path, encoding="utf-8"))
verb = argv[1]
if verb == "resolve":
    answer = scenario.get("resolve", {}).get(argv[argv.index("--commit") + 1], {"exit": 3})
    if "json" in answer:
        print(json.dumps(answer["json"]))
    sys.exit(answer["exit"])
if verb == "get":
    source = scenario.get("get", {}).get(argv[2])
    if source is None:
        print("no such key", file=sys.stderr)
        sys.exit(1)
    shutil.copyfile(source, argv[argv.index("--output") + 1])
    sys.exit(0)
if verb == "put":
    sys.exit(scenario.get("put", {}).get("exit", 0))
sys.exit(1)
"""
