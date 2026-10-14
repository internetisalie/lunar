"""MAINT-37 TC 4, 5, 6, 7, 27 and 47: definition_pins.py."""
import contextlib
import hashlib
import io
import json
import tarfile
import tempfile
import unittest
from pathlib import Path

import definition_pins
from definition_pins import PinError


def write_archive(path: Path, members: dict[str, str]) -> None:
    with tarfile.open(path, "w:gz") as bundle:
        for name, text in members.items():
            data = text.encode()
            info = tarfile.TarInfo(name)
            info.size = len(data)
            bundle.addfile(info, io.BytesIO(data))


def library_for(archive: Path, library_id: str, version: str, urls: list[str]) -> dict:
    return {
        "id": library_id,
        "version": version,
        "urls": urls,
        "sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
        "size": archive.stat().st_size,
        "requires": [],
    }


def catalog_of(*libraries: dict) -> dict[str, dict]:
    return {library["id"]: library for library in libraries}


class ResolveLibrariesTest(unittest.TestCase):
    CATALOG = catalog_of(
        {"id": "busted", "requires": ["luassert"]},
        {"id": "luassert", "requires": []},
        {"id": "love2d", "requires": []},
    )

    def test_TC_4_resolve_adds_the_requires_closure_of_pinned_ids_only(self):
        manifest = {"corpora": [{"name": "a", "definitionLibraries": ["busted"]}, {"name": "b"}]}
        resolved = definition_pins.resolve_libraries(manifest, self.CATALOG)
        self.assertEqual(["busted", "luassert"], [lib["id"] for lib in resolved])

    def test_TC_5_unknown_id_raises_naming_it(self):
        manifest = {"corpora": [{"name": "a", "definitionLibraries": ["nosuch"]}]}
        with self.assertRaises(PinError) as raised:
            definition_pins.resolve_libraries(manifest, self.CATALOG)
        self.assertIn("nosuch", str(raised.exception))


class FetchLibraryTest(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory()
        self.addCleanup(self.scratch.cleanup)
        self.root = Path(self.scratch.name)
        self.archive = self.root / "source.tar.gz"
        write_archive(self.archive, {"p/library/a.lua": "---@meta\n"})
        self.dest = self.root / "dest"
        self.dest.mkdir()

    def test_TC_6_fetch_copies_then_skips_a_verified_archive(self):
        lib = library_for(self.archive, "busted", "v1", [self.archive.as_uri()])
        first = definition_pins.fetch_library(lib, self.dest)
        gone = dict(lib, urls=[(self.root / "missing.tar.gz").as_uri()])
        second = definition_pins.fetch_library(gone, self.dest)
        self.assertEqual(self.dest / "busted-v1.tar.gz", first)
        self.assertEqual(first, second)
        self.assertEqual(self.archive.read_bytes(), second.read_bytes())

    def test_TC_7_digest_mismatch_raises_and_leaves_nothing_behind(self):
        lib = library_for(self.archive, "busted", "v1", [self.archive.as_uri()])
        lib["sha256"] = "0" * 64
        with self.assertRaises(PinError) as raised:
            definition_pins.fetch_library(lib, self.dest)
        message = str(raised.exception)
        self.assertIn("0" * 64, message)
        self.assertIn(hashlib.sha256(self.archive.read_bytes()).hexdigest(), message)
        self.assertEqual([], list(self.dest.iterdir()))


class LibraryGlobalsTest(unittest.TestCase):
    def test_TC_27_only_column_zero_declarations_under_the_root_prefix_count(self):
        with tempfile.TemporaryDirectory() as scratch:
            archive = Path(scratch) / "p.tar.gz"
            library = "function describe(n) end\ncontext = describe\nlocal x = 1\n  function nested() end\n"
            library += "function M.f() end\nx == y\n"
            write_archive(archive, {
                "p/library/a.lua": library,
                "p/spec/b.lua": "function outside() end\n",
                "p/config.json": "{}",
            })
            self.assertEqual({"describe", "context"}, definition_pins.library_globals(archive, "p/library"))


class VerifyTest(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory()
        self.addCleanup(self.scratch.cleanup)
        root = Path(self.scratch.name)
        self.defs = root / "defs"
        self.defs.mkdir()
        write_archive(self.defs / "busted-v.tar.gz", {"busted-v/library/b.lua": "function describe() end\n"})
        write_archive(self.defs / "luassert-w.tar.gz", {"luassert-w/library/l.lua": "function assert_it() end\n"})
        busted = library_for(self.defs / "busted-v.tar.gz", "busted", "v", [])
        busted["requires"] = ["luassert"]
        luassert = library_for(self.defs / "luassert-w.tar.gz", "luassert", "w", [])
        self.catalog = root / "catalog.json"
        self.catalog.write_text(json.dumps({"catalogVersion": 1, "libraries": [busted, luassert]}))
        self.manifest = root / "manifest.json"
        self.manifest.write_text(json.dumps({"corpora": [{"name": "a", "definitionLibraries": ["busted"]}]}))

    def run_verify(self) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        arguments = ["--verify", str(self.defs), "--manifest", str(self.manifest), "--catalog", str(self.catalog)]
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = definition_pins.main(arguments)
        return code, out.getvalue(), err.getvalue()

    def test_TC_47_verify_checks_the_closure_presence_and_digest(self):
        code, out, _ = self.run_verify()
        self.assertEqual(0, code)
        self.assertIn(f"definitions ok (2 archives verified in {self.defs})", out)

        (self.defs / "luassert-w.tar.gz").rename(self.defs / "luassert-w.moved")
        code, _, err = self.run_verify()
        self.assertEqual(1, code)
        self.assertIn("definition_pins: not provisioned: luassert-w.tar.gz", err)

        (self.defs / "luassert-w.moved").rename(self.defs / "luassert-w.tar.gz")
        busted = self.defs / "busted-v.tar.gz"
        busted.write_bytes(bytes(busted.stat().st_size))
        code, _, err = self.run_verify()
        self.assertEqual(1, code)
        self.assertIn("definition_pins: not provisioned: busted-v.tar.gz", err)


if __name__ == "__main__":
    unittest.main()
