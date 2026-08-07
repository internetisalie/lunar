"""TARGET-10 DR-07 spike: an executable reference for the wxLua `.i` grammar.

This is NOT the generator. It exists so that design §3.2/§3.3's parse rules are a claim that was
RUN, not read — every count in design §4.1 and the coverage floors in risks-and-gaps come from it.
The real parser is `tooling/definitions/wxlua/wxi_parser.py`, built in TARGET-10 Phase 1; this file
is its acceptance target and should be deleted once that lands.

Five rules here were discovered only by executing it, and each would have shipped a silently broken
generator (see design §3.2):

  1. `//` line comments must be stripped BEFORE `/* */` spans. `wxaui_aui.i:10` reads
     `// ... include/aui/*.h`; taking block comments first opens block state on that `/*` and
     swallows the remaining ~2400 lines, emitting an EMPTY wxaui namespace with no error.
  2. Multi-line `/** ... */` doxygen blocks must then be discarded. `wxbase_file.i:162` is prose
     containing an unmatched `(`; leaving it in feeds the continuation join and loses wxFileName,
     wxFile, wxDir and wxStandardPaths.
  3. The continuation join must be BOUNDED (10 lines). Unbounded, one stray `(` eats a file.
  4b. Guard chains are EXPRESSIONS: `%A && %B <decl>` (170 lines). Eating one leading token leaves
     `&& <decl>`, which matches no rule -- 85 declarations lost, including wxRealPath and
     wxLocale's constructor.
  4c. `struct` binds exactly like `class` (8 blocks). Without a rule its members escape to file
     scope and `wx.IsCompatible` is emitted as a global function that does not exist.
  5b. `%delete` sits between `class` and the name (487 of 789 class lines), so a line-leading
     attribute strip does not reach it -- and the classes then misparse into ~4800 namespace-level
     free functions rather than simply being skipped.
  5. `%wxEventType` and `%member_func` are declaration keywords, not attributes; an unrestricted
     guard-strip regex removes them and makes their rules unreachable (525 and 14 declarations).

Usage:
    python3 probe.py <path-to>/wxLua/bindings/wxwidgets
"""
import re, os, sys, collections

BIND = sys.argv[1]

# ---- §3.1 namespaces -------------------------------------------------------
rules = {}
for f in sorted(os.listdir(BIND)):
    if f.endswith("_rules.lua"):
        m = re.search(r'hook_lua_namespace\s*=\s*"([^"]*)"',
                      open(os.path.join(BIND, f), encoding="utf-8", errors="replace").read())
        if m and m.group(1):
            rules[f[: -len("_rules.lua")]] = m.group(1)

# ---- §3.2 normalisation ----------------------------------------------------
BLOCK_INLINE = re.compile(r"/\*.*?\*/")
KEEP = ("%wxEventType", "%member_func")            # declaration keywords, never stripped
RENAME = re.compile(r"^\s*%rename\s+(\w+)\s+")
OVERRIDE_NAME = re.compile(r"^\s*%override_name\s+(\w+)\s+")
GUARD = re.compile(r"^\s*(?:!?%[A-Za-z_][A-Za-z0-9_]*\s+)+")
PREPROC = re.compile(r"^\s*#\s*(if|ifdef|ifndef|else|elif|endif|include)\b")


GUARD_TERM = r"!?(?:%[A-Za-z_][A-Za-z0-9_]*|wxUSE_[A-Za-z0-9_]*|wxLUA_USE_[A-Za-z0-9_]*)"

def strip_guards(line):
    """Consume a leading build-condition EXPRESSION, stopping at a declaration keyword.

    A guard is not a token, it is an expression: terms are `%attr`, `wxUSE_*`, `wxLUA_USE_*`,
    optionally `!`-negated, joined by `&&`, `||`, `&`, `|`, and optionally parenthesised. Handling
    only single tokens (or only `&&`/`||`) leaves a bare operator in front of the declaration, which
    matches no anchored rule. Measured over the corpus: 170 `&&`/`||` chains, 8 single `&`/`|`,
    7 mixed `%guard && wxUSE_*`, 1 parenthesised.
    """
    while True:
        m = re.match(r"^\s*\(?\s*(" + GUARD_TERM + r")\s*\)?\s*(?:&&|\|\||&|\|)?\s*", line)
        if not m:
            return line
        tok = m.group(1).lstrip("!")
        if tok in KEEP:
            return line
        line = line[m.end():]


# ---- §3.3 declaration patterns (all anchored) ------------------------------
TYPE = r"(?:(?:const|unsigned|signed|struct|static|virtual|inline)\s+)*[\w:]+(?:\s*<[^>]*>)?(?:\s*[*&]+)?"
R = {
    "evt":     re.compile(r"^\s*%wxEventType\s+(\w+)"),
    "defobj2": re.compile(r"^\s*#define_object\s+(\w+)\s+(\w+)\s*;?\s*$"),
    "defobj1": re.compile(r"^\s*#define_object\s+(\w+)\s*;?\s*$"),
    "defptr":  re.compile(r"^\s*#define_pointer\s+(\w+)"),
    "defstr":  re.compile(r"^\s*#define_(?:string|wxstring)\s+(\w+)"),
    "define":  re.compile(r"^\s*#define\s+(\w+)\s*$"),
    "enum":    re.compile(r"^\s*enum\b\s*(\w+(?:::\w+)*)?"),
    # `struct` is bound exactly like `class` (8 blocks, 7 emitted types). Omitting it does not
    # merely drop them -- their members escape to file scope and `wx.IsCompatible` is INVENTED.
    "clsfwd":  re.compile(r"^\s*(?:class|struct)\s+(?:%\w+\s+)*(\w+(?:::\w+)*)\s*;\s*$"),
    # NOTE the `[\w:]+` name group: 6 classes and 29 enums are declared `Parent::Name`. A `(\w+)`
    # group stops at the `::` and captures the PARENT -- so the nested type's members merge into the
    # real parent class (wxDateTime gains TimeZone's Make/GetOffset) and 18 enum aliases collide
    # with real class names. Corruption, not loss.
    "cls":     re.compile(r"^\s*(?:class|struct)\s+(?:%\w+\s+)*(\w+(?:::\w+)*)\s*(?::\s*(?:public|protected|private)\s+(\w+))?"),
    "memfn":   re.compile(r"^\s*%member_func\s+(" + TYPE + r")\s+(\w+)\s*;"),
    "enummem": re.compile(r"^\s*(\w+)\s*(?:=[^,]*)?,?\s*$"),
    "op":      re.compile(r"^\s*.*\boperator\b"),
    "func":    re.compile(r"^\s*(?P<st>static\s+)?(?:virtual\s+)?(?P<ret>" + TYPE + r")\s+(?P<nm>\w+)\s*\((?P<args>.*)\)\s*(?:const\s*)?(?:%\w+\s*)?(?:=\s*0\s*)?;"),
    "ctor":    re.compile(r"^\s*(?P<nm>\w+)\s*\((?P<args>.*)\)\s*(?:%\w+\s*)?;"),
}

stats = collections.Counter()
unrec = []
groups = collections.defaultdict(lambda: {"consts": [], "classes": {}, "funcs": [], "aliases": []})

for fn in sorted(os.listdir(BIND)):
    if not fn.endswith(".i"):
        continue
    ns = rules.get(fn.split("_")[0])
    if not ns:
        continue
    g = groups[ns]
    in_block = False
    pending = ""
    pending_lines = 0
    cls = None            # (name, depth)
    en = None             # depth
    export = None
    for lineno, raw in enumerate(open(os.path.join(BIND, fn), encoding="utf-8", errors="replace"), 1):
        line = raw.rstrip("\n")
        # 1 LINE comments first (a `/*` inside a `//` would otherwise open block state)
        if line.lstrip().startswith("///"):
            continue
        if line.lstrip().startswith("//"):
            continue
        line = line.split("//", 1)[0]
        # 2 inline block-comment spans, then MULTI-LINE block state (balanced once // is gone)
        line = BLOCK_INLINE.sub(" ", line)
        if in_block:
            if "*/" in line:
                line, in_block = line.split("*/", 1)[1], False
            else:
                continue
        if "/*" in line:
            line, in_block = line.split("/*", 1)[0], True
        if not line.strip():
            continue
        # 3 detach pointer/reference sigils from the name: `T *Name(` -> `T * Name(`
        line = re.sub(r"([*&]+)", r" \1 ", line)
        # 3b continuation join: a declaration may span lines
        if pending:
            line, pending = pending + " " + line.strip(), ""
        if line.count("(") > line.count(")"):
            pending = line if pending_lines < 10 else ""   # bounded: never swallow a file
            pending_lines = pending_lines + 1 if pending else 0
            continue
        pending_lines = 0
        # 4 name capture then guard strip
        m = RENAME.match(line)
        if m:
            export, line = m.group(1), line[m.end():]
        m = OVERRIDE_NAME.match(line)
        if m:
            line = line[m.end():]
        line = re.sub(r"^\s*(?:wxUSE_|wxLUA_USE_)[A-Za-z0-9_]*(?:\s*[|&]\s*[(%A-Za-z0-9_|&)]+)*\s+", " ", line)
        line = strip_guards(line)
        # 4b remove any remaining %attr token anywhere on the line (e.g. `virtual %gc T *F();`),
        #    except the declaration keywords
        line = re.sub(r"(?<!\S)!?%(?!wxEventType\b|member_func\b)[A-Za-z_][A-Za-z0-9_]*(?!\S)", " ", line)
        # 5 preprocessor
        if PREPROC.match(line):
            continue
        if not line.strip():
            continue

        def take(kind):
            stats[kind] += 1

        nm = lambda d: export or d

        if en is not None:                       # inside an enum
            if "{" in line:
                en += line.count("{")
            if "}" in line:
                en -= line.count("}")
                if en <= 0:
                    en = None
                    export = None
                    continue
            m = R["enummem"].match(line)
            if m:
                g["consts"].append(m.group(1)); take("enum_member")
            export = None
            continue

        if R["op"].match(line):
            take("operator_skipped"); export = None; continue
        m = R["evt"].match(line)
        if m: g["consts"].append(nm(m.group(1))); take("evt"); export=None; continue
        m = R["defobj2"].match(line)
        if m: g["consts"].append(nm(m.group(2))); take("define_object"); export=None; continue
        m = R["defobj1"].match(line)
        if m: g["consts"].append(nm(m.group(1))); take("define_object"); export=None; continue
        m = R["defptr"].match(line)
        if m: g["consts"].append(nm(m.group(1))); take("define_pointer"); export=None; continue
        m = R["defstr"].match(line)
        if m: g["consts"].append(nm(m.group(1))); take("define_string"); export=None; continue
        m = R["define"].match(line)
        if m: g["consts"].append(nm(m.group(1))); take("define"); export=None; continue
        m = R["enum"].match(line)
        if m:
            if m.group(1): g["aliases"].append(m.group(1).split("::")[-1])
            en = line.count("{")
            take("enum"); export=None; continue
        m = R["clsfwd"].match(line)
        if m: take("class_forward"); export=None; continue
        m = R["cls"].match(line)
        if m:
            cname = m.group(1).replace("::", "_")   # nested type gets its own flat name
            g["classes"].setdefault(cname, {"base": m.group(2), "methods": [], "statics": [], "ctors": []})
            cls = [cname, line.count("{")]
            take("class"); export=None; continue

        if cls is not None:
            cls[1] += line.count("{") - line.count("}")
            if cls[1] <= 0 and ("}" in line):
                cls = None; export = None; continue
        if cls is not None:
            m = R["memfn"].match(line)
            if m:
                if export:
                    g["classes"][cls[0]]["methods"].append(export); take("member_func")
                else:
                    take("member_func_no_rename")
                export = None; continue
            m = R["func"].match(line)
            if m:
                bucket = "statics" if m.group("st") else "methods"
                g["classes"][cls[0]][bucket].append(nm(m.group("nm"))); take("method"); export=None; continue
            m = R["ctor"].match(line)
            if m and (m.group("nm") == cls[0] or export):
                g["classes"][cls[0]]["ctors"].append(export or cls[0]); take("ctor"); export=None; continue
        else:
            m = R["func"].match(line)
            if m:
                g["funcs"].append(nm(m.group("nm"))); take("free_function"); export=None; continue

        take("rule_ignored")
        if line.strip().endswith(";"):
            unrec.append(f"{fn}:{lineno}: {line.strip()[:100]}")
        export = None

print("=== rule firing counts ===")
for k, v in sorted(stats.items(), key=lambda kv: -kv[1]):
    print(f"{v:7d}  {k}")
print("\n=== per namespace ===")
for ns, g in sorted(groups.items()):
    print(f"{ns:10s} consts={len(set(g['consts'])):5d} classes={len(g['classes']):4d} "
          f"methods={sum(len(c['methods']) for c in g['classes'].values()):5d} "
          f"ctors={sum(len(c['ctors']) for c in g['classes'].values()):4d} "
          f"statics={sum(len(c['statics']) for c in g['classes'].values()):4d} "
          f"free={len(set(g['funcs'])):4d} aliases={len(set(g['aliases'])):3d}")
print(f"\n=== unrecognised lines ending in ';' : {len(unrec)} ===")
for u in unrec[:15]:
    print("  ", u)
import json
json.dump({ns: sorted(set(g["consts"]) | set(g["classes"]) | set(g["funcs"]))
           for ns, g in groups.items()}, open(os.path.join(os.path.dirname(__file__), "names2.json"), "w"))
