"""Tests for third_party_notices.py (tasks.md T087, S4).

They use made-up manifests and checkouts, so they need no Xcode and run on
any machine: python3 -m unittest discover -s apps/ios/scripts -p "test_*.py"
"""

import tempfile
import unittest
from pathlib import Path

import third_party_notices as tpn


def product(name, package, condition=None):
    return {"product": [name, package, None, condition]}


def by_name(name, condition=None):
    return {"byName": [name, condition]}


def manifest(name, products, targets, dependencies=()):
    return {
        "name": name,
        "products": [{"name": p, "targets": t, "type": {"library": ["automatic"]}} for p, t in products],
        "targets": [{"name": n, "type": kind, "dependencies": deps} for n, kind, deps in targets],
        "dependencies": [{"sourceControl": [{"identity": identity, "location": {"remote": [{"urlString": url}]}}]}
                         for identity, url in dependencies],
    }


class Fixture:
    """An app that links ZZCore (local) and LibA, which links LibB. LibA also
    has a test target on a test-only package and a build-tool plugin package."""

    def __init__(self):
        self.root = Path(tempfile.mkdtemp())
        self.remote = {}
        self.manifests = {}
        self.local = self.add_dir("Packages/ZZCore")
        self.manifests[self.local] = manifest("ZZCore", [("ZZCore", ["ZZCore"])], [("ZZCore", "regular", [])])
        self.add_remote("lib-a", "https://example.com/org/lib-a.git", manifest(
            "LibA", [("LibA", ["LibA"])],
            [("LibA", "regular", [product("LibB", "lib-b"), product("LinuxOnly", "linux-only", {"platformNames": ["linux"]})]),
             ("LibATests", "test", [by_name("LibA"), product("Snapshots", "test-only")]),
             ("Generate", "plugin", [product("Tool", "tool")])],
            [("lib-b", "https://example.com/org/lib-b"), ("test-only", "https://example.com/org/test-only"),
             ("tool", "https://example.com/org/tool"), ("linux-only", "https://example.com/org/linux-only")]),
            notice=True)
        self.add_remote("lib-b", "https://example.com/org/lib-b", manifest(
            "LibB", [("LibB", ["LibB", "LibBCore"])],
            [("LibB", "regular", [by_name("LibBCore")]), ("LibBCore", "regular", [])]))
        for identity in ("test-only", "tool", "linux-only"):
            self.add_remote(identity, f"https://example.com/org/{identity}", manifest(identity, [], []))
        self.direct = [("ZZCore", None), ("LibA", "https://example.com/org/LIB-A")]
        self.pins = [{"identity": identity, "location": location, "state": {"version": "1.2.3"}}
                     for identity, (_, location) in self.remote.items()]

    def add_dir(self, relative):
        path = self.root / relative
        path.mkdir(parents=True)
        return path

    def add_remote(self, identity, location, data, license_file=True, notice=False):
        path = self.add_dir(f"checkouts/{identity}")
        if license_file:
            (path / "LICENSE.txt").write_text(f"{identity} license text\n", encoding="utf-8")
        if notice:
            (path / "NOTICE.txt").write_text(f"{identity} notice text\n", encoding="utf-8")
        self.manifests[path] = data
        self.remote[identity] = (path, location)

    def closure(self):
        return tpn.closure(self.direct, [self.local], self.remote, lambda d: self.manifests[Path(d)])


class ClosureTests(unittest.TestCase):
    def test_the_closure_is_the_linked_library_targets(self):
        packages = Fixture().closure()
        self.assertEqual(set(packages), {"zzcore", "lib-a", "lib-b"})
        self.assertEqual(packages["lib-a"]["targets"], {"LibA"})
        self.assertEqual(packages["lib-b"]["targets"], {"LibB", "LibBCore"})

    def test_test_targets_plugins_and_other_platforms_are_left_out(self):
        packages = Fixture().closure()
        for identity in ("test-only", "tool", "linux-only"):
            self.assertNotIn(identity, packages)

    def test_an_unknown_product_fails(self):
        fixture = Fixture()
        fixture.direct.append(("Missing", None))
        with self.assertRaises(tpn.NoticesError):
            fixture.closure()


class LinkedCheckTests(unittest.TestCase):
    def link_list(self, fixture, names):
        built = fixture.add_dir("Build/Products")
        lines = [str(fixture.root / "Intermediates/App.o")] + [str(built / f"{n}.o") for n in names]
        path = fixture.root / "App.LinkFileList"
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return [path], built

    def test_only_package_objects_count(self):
        fixture = Fixture()
        lists, built = self.link_list(fixture, ["ZZCore", "LibA", "LibB", "LibBCore"])
        self.assertEqual(tpn.linked_targets(lists, built), {"ZZCore", "LibA", "LibB", "LibBCore"})
        tpn.check_linked(tpn.linked_targets(lists, built), fixture.closure())

    def test_a_linked_package_missing_from_the_notices_fails_the_build(self):
        fixture = Fixture()
        lists, built = self.link_list(fixture, ["ZZCore", "LibA", "LibB", "LibBCore", "Snapshots"])
        with self.assertRaisesRegex(tpn.NoticesError, "Snapshots"):
            tpn.check_linked(tpn.linked_targets(lists, built), fixture.closure())


class NoticesTextTests(unittest.TestCase):
    def test_each_package_has_its_version_repository_license_and_notice(self):
        fixture = Fixture()
        text = tpn.notices_text(fixture.closure(), fixture.pins)
        blocks = text.split(f"\n\n{tpn.SEPARATOR}\n\n")
        self.assertEqual(len(blocks), 2, "local packages are zzThat's own and are not listed")
        self.assertTrue(blocks[0].startswith("LibA 1.2.3\nhttps://example.com/org/lib-a.git"))
        self.assertIn("lib-a license text", blocks[0])
        self.assertIn("lib-a notice text", blocks[0])
        self.assertTrue(blocks[1].startswith("LibB 1.2.3\n"))
        self.assertNotIn("test-only", text)

    def test_a_listed_package_without_a_license_fails_the_build(self):
        fixture = Fixture()
        (fixture.remote["lib-b"][0] / "LICENSE.txt").unlink()
        with self.assertRaisesRegex(tpn.NoticesError, "lib-b 1.2.3 has no license file"):
            tpn.notices_text(fixture.closure(), fixture.pins)

    def test_a_listed_package_that_is_not_pinned_fails_the_build(self):
        fixture = Fixture()
        pins = [pin for pin in fixture.pins if pin["identity"] != "lib-b"]
        with self.assertRaisesRegex(tpn.NoticesError, "lib-b is linked but not pinned"):
            tpn.notices_text(fixture.closure(), pins)


if __name__ == "__main__":
    unittest.main()
