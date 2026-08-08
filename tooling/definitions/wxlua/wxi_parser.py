#!/usr/bin/env python3
"""Parse wxLua's `.i` binding-interface files into a namespace -> declarations model.

Realizes TARGET-10 design §3.1 (namespace discovery), §3.2 (line normalisation), §3.3 (declaration
recognition) and §3.3b (parameter lists). Knows the `.i` grammar and nothing about LuaCATS.

THE ORDER OF §3.2's STEPS IS LOAD-BEARING. Five of them exist only because an earlier, plausible
version was executed and silently produced wrong output; each carries its measured cost below. The
grammar is empirical: `.i` is a hand-maintained, undocumented format with no upstream spec, so
`residue()` exists to make what the rules do NOT recognise inspectable rather than invisible.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

# --------------------------------------------------------------------------- model


@dataclass(frozen=True)
class Param:
    name: str
    type: str
    optional: bool = False


@dataclass(frozen=True)
class Func:
    name: str
    params: tuple[Param, ...] = ()
    returns: str | None = None
    doc: str = ""
    static: bool = False


@dataclass
class Klass:
    name: str
    base: str | None = None
    doc: str = ""
    ctors: list[Func] = field(default_factory=list)
    methods: list[Func] = field(default_factory=list)
    statics: list[Func] = field(default_factory=list)


@dataclass(frozen=True)
class Const:
    name: str
    type: str = "number"
    doc: str = ""


@dataclass
class Group:
    namespace: str
    cpp: str
    classes: dict[str, Klass] = field(default_factory=dict)
    funcs: list[Func] = field(default_factory=list)
    consts: list[Const] = field(default_factory=list)
    aliases: list[str] = field(default_factory=list)
    residue: list[str] = field(default_factory=list)
    stats: dict[str, int] = field(default_factory=dict)


# --------------------------------------------------------------------------- §3.1


NAMESPACE_HOOK = re.compile(r'hook_lua_namespace\s*=\s*"([^"]*)"')


def namespaces(bindings_dir: Path) -> dict[str, str]:
    """C++ group prefix -> Lua namespace, from each `<cpp>_rules.lua`'s `hook_lua_namespace`.

    An empty namespace means the group emits no Lua surface (`wxdatatypes`) and is skipped, by the
    value rather than by name so a future empty group behaves the same.
    """
    found: dict[str, str] = {}
    for path in sorted(bindings_dir.iterdir()):
        if not path.name.endswith("_rules.lua"):
            continue
        match = NAMESPACE_HOOK.search(path.read_text(encoding="utf-8", errors="replace"))
        if match and match.group(1):
            found[path.name[: -len("_rules.lua")]] = match.group(1)
    return found


# --------------------------------------------------------------------------- §3.2

BLOCK_INLINE = re.compile(r"/\*.*?\*/")
DECLARATION_KEYWORDS = ("%wxEventType", "%member_func")
RENAME = re.compile(r"^\s*%rename\s+(\w+)\s+")
OVERRIDE_NAME = re.compile(r"^\s*%override_name\s+(\w+)\s+")
GUARD_TERM = r"!?(?:%[A-Za-z_][A-Za-z0-9_]*|wxUSE_[A-Za-z0-9_]*|wxLUA_USE_[A-Za-z0-9_]*)"
GUARD_LEAD = re.compile(r"^\s*\(?\s*(" + GUARD_TERM + r")\s*\)?\s*(?:&&|\|\||&|\|)?\s*")
ATTR_ANYWHERE = re.compile(
    r"(?<!\S)!?%(?!wxEventType\b|member_func\b)[A-Za-z_][A-Za-z0-9_]*(?!\S)",
)
PREPROCESSOR = re.compile(r"^\s*#\s*(?:if|ifdef|ifndef|else|elif|endif|include)\b")
MAX_CONTINUATION_LINES = 10
MAX_NAME_GUARD_PASSES = 4


def _consume_guard_expression(line: str) -> str:
    """Remove a leading build-condition EXPRESSION, stopping at a declaration keyword.

    A guard is not a token. Terms are `%attr` / `wxUSE_*` / `wxLUA_USE_*`, optionally `!`-negated,
    joined by `&&`, `||`, `&`, `|`, and optionally parenthesised. Measured: 170 `&&`/`||` chains,
    8 single `&`/`|`, 7 mixed with `wxUSE_*`, 1 parenthesised. Consuming only one leading token
    leaves a bare operator in front of the declaration, which matches no anchored rule -- 85
    declarations lost, including wxRealPath and wxLocale's constructor.
    """
    while True:
        match = GUARD_LEAD.match(line)
        if not match or match.group(1).lstrip("!") in DECLARATION_KEYWORDS:
            return line
        line = line[match.end() :]


class _Normaliser:
    """§3.2, as a small state machine: block-comment state, doc capture, continuation join."""

    def __init__(self) -> None:
        self.in_block = False
        self.pending = ""
        self.pending_lines = 0
        self.doc: list[str] = []
        self.export: str | None = None

    def feed(self, raw: str) -> str | None:
        """One physical line -> a normalised declaration line, or None if it yields none."""
        line = raw.rstrip("\n")

        # 1. LINE comments FIRST. `wxaui_aui.i:10` is `// ... include/aui/*.h`; taking block
        #    comments first opens block state on that `/*` and swallows ~2400 lines, emitting an
        #    EMPTY wxaui namespace with no error. Measured: 8 of 42 files have unbalanced `/*`
        #    before this step, 0 of 42 after.
        stripped = line.lstrip()
        if stripped.startswith("///"):
            self.doc.append(stripped[3:].strip())
            return None
        if stripped.startswith("//"):
            return None
        line = line.split("//", 1)[0]

        # 2/3. Inline `/* */` spans, then multi-line block state (safe only because step 1 ran).
        #      `wxbase_file.i:162` is doxygen prose with an unmatched `(`; without block state it
        #      feeds the continuation join and loses wxFileName, wxFile, wxDir, wxStandardPaths.
        line = BLOCK_INLINE.sub(" ", line)
        if self.in_block:
            if "*/" not in line:
                return None
            line, self.in_block = line.split("*/", 1)[1], False
        if "/*" in line:
            line, self.in_block = line.split("/*", 1)[0], True
        if not line.strip():
            if not self.pending:
                self.doc.clear()
            return None

        # 4. Detach pointer/reference sigils: `T *Name(` -> `T * Name(`. `wxDataViewModel
        #    *GetOwner()` binds the `*` to the NAME, so a <type><space><name> pattern cannot match.
        #    Measured: recovers 215 declarations.
        line = re.sub(r"([*&]+)", r" \1 ", line)

        # 5. Bounded continuation join -- declarations span lines (264 of them). BOUNDED because
        #    unbounded, one stray `(` in prose consumes the rest of the file.
        if self.pending:
            line, self.pending = self.pending + " " + line.strip(), ""
        if line.count("(") > line.count(")"):
            if self.pending_lines < MAX_CONTINUATION_LINES:
                self.pending = line
                self.pending_lines += 1
            else:
                self.pending_lines = 0
            return None
        self.pending_lines = 0

        # 6/7. Export-name capture and guard consumption INTERLEAVE to a fixed point: 12 lines put
        #      a guard before `%rename`, and one puts `%override_name` before it too. Running the
        #      guard strip first would eat `%rename` and destroy both the name and the declaration.
        for _ in range(MAX_NAME_GUARD_PASSES):
            before = line
            match = RENAME.match(line)
            if match:
                # %rename BINDS the exported Lua name. `%rename GetPositionXY void GetPosition()`
                # binds GetPositionXY; dropping it emits a method that does not exist AND omits
                # the one that does.
                self.export, line = match.group(1), line[match.end() :]
            match = OVERRIDE_NAME.match(line)
            if match:
                # Its argument is a C++ symbol, not a Lua name -- capture and discard. The one
                # `%`-token that looks like %rename and must not be treated like it.
                line = line[match.end() :]
            line = _consume_guard_expression(line)
            if line == before:
                break

        # 8. Any remaining `%attr`, anywhere. `%delete` sits BETWEEN `class` and the name (487 of
        #    789 class lines); a line-anchored strip leaves it, the class rule fails, and every
        #    member falls to file scope -- ~4800 methods emitted as global functions.
        line = ATTR_ANYWHERE.sub(" ", line)

        # 9. Preprocessor lines are consumed and ignored: every branch of every conditional is
        #    emitted (design §3.2 -- guards resolve against the user's wxWidgets build).
        if PREPROCESSOR.match(line):
            return None
        return line if line.strip() else None

    def take_doc(self) -> str:
        doc = " ".join(part for part in self.doc if part).strip()
        self.doc.clear()
        return doc

    def take_export(self) -> str | None:
        export, self.export = self.export, None
        return export


# --------------------------------------------------------------------------- §3.3

TYPE = (
    r"(?:(?:const|unsigned|signed|struct|static|virtual|inline)\s+)*"
    r"[\w:]+(?:\s*<[^>]*>)?(?:\s*[*&]+)?"
)
RULES = {
    "event": re.compile(r"^\s*%wxEventType\s+(\w+)"),
    "define_object_typed": re.compile(r"^\s*#define_object\s+(\w+)\s+(\w+)\s*;?\s*$"),
    "define_object": re.compile(r"^\s*#define_object\s+(\w+)\s*;?\s*$"),
    "define_pointer": re.compile(r"^\s*#define_pointer\s+(\w+)"),
    "define_string": re.compile(r"^\s*#define_(?:string|wxstring)\s+(\w+)"),
    "define": re.compile(r"^\s*#define\s+(\w+)\s*$"),
    "enum": re.compile(r"^\s*enum\b\s*(\w+(?:::\w+)*)?"),
    "enum_member": re.compile(r"^\s*(\w+)\s*(?:=[^,]*)?,?\s*$"),
    # `[\w:]`-aware name group: 6 classes and 29 enums are declared `Parent::Name`. A `(\w+)` group
    # stops at the `::` and captures the PARENT, so the nested type's members merge into the real
    # parent class (wxDateTime gains TimeZone's Make/GetOffset) and 18 enum aliases collide with
    # class names. Corruption, not loss -- and invisible to both the residue and the ratchet.
    "class_forward": re.compile(r"^\s*(?:class|struct)\s+(\w+(?:::\w+)*)\s*;\s*$"),
    "class": re.compile(
        r"^\s*(?:class|struct)\s+(\w+(?:::\w+)*)\s*"
        r"(?::\s*(?:public|protected|private)\s+(\w+))?",
    ),
    "operator": re.compile(r"^\s*.*\boperator\b"),
    "member_func": re.compile(r"^\s*%member_func\s+(" + TYPE + r")\s+(\w+)\s*;"),
    "function": re.compile(
        r"^\s*(?P<static>static\s+)?(?:virtual\s+)?(?P<ret>" + TYPE + r")\s+"
        r"(?P<name>\w+)\s*\((?P<args>.*)\)\s*(?:const\s*)?(?:=\s*0\s*)?;",
    ),
    "ctor": re.compile(r"^\s*(?P<name>\w+)\s*\((?P<args>.*)\)\s*;"),
}

LUA_KEYWORDS = {
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in",
    "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while",
}


def _split_top_level(text: str) -> list[str]:
    """Split on commas at nesting depth 0, counting `(`/`[`/`<` as opening."""
    parts, depth, current = [], 0, []
    for char in text:
        if char in "([<":
            depth += 1
        elif char in ")]>":
            depth -= 1
        if char == "," and depth <= 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(char)
    if current:
        parts.append("".join(current))
    return parts


def parse_params(args: str, map_type) -> tuple[Param, ...]:
    """§3.3b. Raw text between the parentheses -> Param tuple."""
    args = args.strip()
    if not args or args == "void":
        return ()
    params: list[Param] = []
    optional_from_here = False
    for index, fragment in enumerate(_split_top_level(args), start=1):
        fragment = fragment.strip()
        if not fragment:
            continue
        if fragment.startswith("..."):
            params.append(Param("...", "any"))
            continue
        optional = False
        if "=" in fragment:
            fragment = fragment.split("=", 1)[0]
            optional = True
        fragment = re.sub(r"\b(?:const)\b", " ", fragment)
        fragment = fragment.replace("*", " ").replace("&", " ")
        fragment = re.sub(r"\[\s*\]", " ", fragment).strip()
        tokens = fragment.split()
        if not tokens:
            continue
        if len(tokens) >= 2:
            name, type_tokens = tokens[-1], tokens[:-1]
        else:
            name, type_tokens = f"arg{index}", tokens
        if name in LUA_KEYWORDS:
            name += "_"
        optional_from_here = optional_from_here or optional
        params.append(Param(name, map_type(" ".join(type_tokens)), optional_from_here))
    return tuple(params)


def parse_group(bindings_dir: Path, cpp: str, namespace: str, map_type) -> Group:
    """All `<cpp>_*.i` files -> one Group. `map_type` is injected so this module stays LuaCATS-free."""
    group = Group(namespace=namespace, cpp=cpp)
    for path in sorted(bindings_dir.glob(f"{cpp}_*.i")):
        _parse_file(path, group, map_type)
    return group


def _bump(group: Group, kind: str) -> None:
    group.stats[kind] = group.stats.get(kind, 0) + 1


def _parse_file(path: Path, group: Group, map_type) -> None:
    normaliser = _Normaliser()
    current_class: str | None = None
    class_depth = 0
    enum_depth: int | None = None

    for lineno, raw in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        line = normaliser.feed(raw)
        if line is None:
            continue
        export = normaliser.take_export()
        doc = normaliser.take_doc()
        name_of = lambda declared: export or declared  # noqa: E731 -- %rename wins where present

        # Enum state is checked BEFORE class state: 11 enums are declared inside class bodies, and
        # a single flat counter per state is only sufficient in that order.
        if enum_depth is not None:
            enum_depth += line.count("{") - line.count("}")
            if "}" in line and enum_depth <= 0:
                enum_depth = None
                continue
            if "{" in line:
                continue
            match = RULES["enum_member"].match(line)
            if match:
                group.consts.append(Const(match.group(1), "number", doc))
                _bump(group, "enum_member")
            continue

        if RULES["operator"].match(line):
            # C++ operators are unreachable from Lua by name AND by metamethod: genwxbind.lua has
            # no metamethod mapping, and wxlbind.cpp binds only __index/__tostring. Verified, so
            # BUG-424 (which models metamethods) creates no gap here.
            _bump(group, "operator_skipped")
            continue

        for kind, group_index, const_type in (
            ("event", 1, "number"),
            ("define_object_typed", 2, None),
            ("define_object", 1, "any"),
            ("define_pointer", 1, "any"),
            ("define_string", 1, "string"),
            ("define", 1, "number"),
        ):
            match = RULES[kind].match(line)
            if not match:
                continue
            declared = match.group(group_index)
            resolved = map_type(match.group(1)) if const_type is None else const_type
            group.consts.append(Const(name_of(declared), resolved, doc))
            _bump(group, kind)
            break
        else:
            match = RULES["enum"].match(line)
            if match:
                if match.group(1):
                    # Alias takes the LAST `::` segment, to agree with what map_type produces for a
                    # parameter typed `wxDateTime::Month`. Disagreement silently degrades 95 sites.
                    group.aliases.append(match.group(1).split("::")[-1])
                enum_depth = line.count("{")
                _bump(group, "enum")
                continue

            if RULES["class_forward"].match(line):
                _bump(group, "class_forward")
                continue

            match = RULES["class"].match(line)
            if match:
                name = match.group(1).replace("::", "_")
                existing = group.classes.get(name)
                if existing is None:
                    group.classes[name] = Klass(name=name, base=match.group(2), doc=doc)
                elif existing.base is None:
                    existing.base = match.group(2)
                current_class, class_depth = name, line.count("{")
                _bump(group, "class")
                continue

            if current_class is not None:
                class_depth += line.count("{") - line.count("}")
                if "}" in line and class_depth <= 0:
                    current_class = None
                    continue

            if current_class is not None:
                klass = group.classes[current_class]
                match = RULES["member_func"].match(line)
                if match:
                    if export:
                        klass.methods.append(Func(export, (), map_type(match.group(1)), doc))
                        _bump(group, "member_func")
                    continue
                match = RULES["function"].match(line)
                if match:
                    func = _build_func(match, name_of, map_type, doc)
                    (klass.statics if match.group("static") else klass.methods).append(func)
                    _bump(group, "method")
                    continue
                match = RULES["ctor"].match(line)
                if match and (match.group("name") == current_class or export):
                    klass.ctors.append(
                        Func(
                            name=export or current_class,
                            params=parse_params(match.group("args"), map_type),
                            returns=current_class,
                            doc=doc,
                        ),
                    )
                    _bump(group, "ctor")
                    continue
            else:
                match = RULES["function"].match(line)
                if match:
                    group.funcs.append(_build_func(match, name_of, map_type, doc))
                    _bump(group, "free_function")
                    continue

            _bump(group, "unrecognised")
            if line.strip().endswith(";"):
                group.residue.append(f"{path.name}:{lineno}: {line.strip()[:120]}")


def _build_func(match, name_of, map_type, doc: str) -> Func:
    returns = match.group("ret").strip()
    stripped = re.sub(r"\b(?:static|virtual|inline|const)\b", " ", returns).strip()
    return Func(
        name=name_of(match.group("name")),
        params=parse_params(match.group("args"), map_type),
        returns=None if stripped in ("void", "") else map_type(returns),
        doc=doc,
        static=bool(match.group("static")),
    )
