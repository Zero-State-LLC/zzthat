#!/usr/bin/env python3
"""Generate the iOS third-party notices at build time (tasks.md T087, S4).

The notices list only the packages linked into the shipped app: the
transitive closure of the app target's package products through their
library targets, read from the package manifests. Test targets, test-only
packages, macros, and build-tool plugins are left out. For each package the
file gives its name, version, and repository, its full license text, and its
NOTICE text when it has one (Apache 2.0). The Licenses screen shows the file
as given, after the zzThis NOTICE and OFL.txt.

The build fails, naming the cause, when:
  - a package target the app's linker read is not in the notices;
  - a listed package has no license file, or is not pinned in the lock;
  - a manifest cannot be read, or a product or package cannot be found.
Nothing is skipped silently. Standard library only, plus the Xcode tools
already on the build machine (plutil, and swift for the manifests).

Usage:
  third_party_notices.py --project zzThat.xcodeproj --target zzThat
      --resolved Package.resolved --source-packages DIR
      --built-products DIR --link-file-list FILE [FILE ...]
      --cache DIR --out FILE
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

SEPARATOR = "=" * 64
LICENSE_NAME = re.compile(r"^(licen[cs]e|copying)([.-].*)?$", re.IGNORECASE)
NOTICE_NAME = re.compile(r"^notice([.-].*)?$", re.IGNORECASE)
# Targets that are not linked into the app.
NOT_LINKED = {"test", "plugin", "macro", "executable"}


class NoticesError(Exception):
    pass


def fail(message):
    raise NoticesError(message)


def read_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        fail(f"cannot read {path}: {exc}")


def url_key(url):
    """One spelling for a repository URL: lowercase, no .git, no slash."""
    url = url.strip().lower().rstrip("/")
    return url[:-4] if url.endswith(".git") else url


# MARK: The app target and the packages

def app_products(project, target):
    """The app target's package products: (product name, remote URL or None)."""
    pbxproj = Path(project) / "project.pbxproj"
    try:
        raw = subprocess.run(["/usr/bin/plutil", "-convert", "json", "-o", "-", str(pbxproj)],
                             check=True, capture_output=True).stdout
    except (OSError, subprocess.CalledProcessError) as exc:
        fail(f"cannot read {pbxproj}: {exc}")
    objects = json.loads(raw)["objects"]
    targets = [o for o in objects.values() if o.get("isa") == "PBXNativeTarget" and o.get("name") == target]
    if len(targets) != 1:
        fail(f"no single target named {target} in {project}")
    products = []
    for ref in targets[0].get("packageProductDependencies", []):
        dependency = objects[ref]
        package = objects.get(dependency.get("package"), {})
        products.append((dependency["productName"], package.get("repositoryURL")))
    locals_ = [o["relativePath"] for o in objects.values() if o.get("isa") == "XCLocalSwiftPackageReference"]
    return products, [Path(project).parent / path for path in locals_]


def remote_packages(source_packages):
    """Identity to (checkout dir, location) for the packages the build resolved."""
    state = read_json(Path(source_packages) / "workspace-state.json")
    packages = {}
    for dep in state.get("object", {}).get("dependencies", []):
        ref = dep.get("packageRef", {})
        if ref.get("kind") == "remoteSourceControl":
            packages[ref["identity"].lower()] = (Path(source_packages) / "checkouts" / dep["subpath"],
                                                 ref.get("location", ""))
    return packages


def load_manifest(directory, cache):
    """`swift package dump-package`, cached by the manifest's own bytes."""
    manifests = sorted(Path(directory).glob("Package*.swift"))
    if not manifests:
        fail(f"no Package.swift in {directory}")
    digest = hashlib.sha256(b"".join(m.read_bytes() for m in manifests)).hexdigest()
    cached = Path(cache) / f"{digest}.json"
    if cached.is_file():
        return json.loads(cached.read_text(encoding="utf-8"))
    # A clean environment, so the iOS build settings do not reach the host
    # compiler that evaluates the manifest.
    env = {"PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "HOME": os.environ.get("HOME", "/tmp")}
    if "DEVELOPER_DIR" in os.environ:
        env["DEVELOPER_DIR"] = os.environ["DEVELOPER_DIR"]
    scratch = Path(cache) / "scratch" / digest
    try:
        out = subprocess.run(["/usr/bin/xcrun", "--sdk", "macosx", "swift", "package", "dump-package",
                              "--package-path", str(directory), "--scratch-path", str(scratch)],
                             check=True, capture_output=True, env=env).stdout
        manifest = json.loads(out)
    except (OSError, subprocess.CalledProcessError, ValueError) as exc:
        detail = getattr(exc, "stderr", b"") or b""
        fail(f"cannot read the manifest in {directory}: {exc} {detail.decode(errors='replace')[:400]}")
    Path(cache).mkdir(parents=True, exist_ok=True)
    cached.write_text(json.dumps(manifest), encoding="utf-8")
    return manifest


# MARK: The closure

def applies_to_ios(condition):
    names = (condition or {}).get("platformNames")
    return not names or "ios" in names


def closure(direct, local_dirs, remote, load):
    """The package targets the app links, by package key.

    direct: (product name, remote URL or None) pairs from the app target.
    local_dirs: the project's local package folders.
    remote: identity -> (checkout dir, location).
    load: directory -> manifest dict.
    Returns {package key: {"dir", "location", "local", "targets": set()}}.
    """
    packages = {}

    def package(key, directory, location, local):
        if key not in packages:
            packages[key] = {"dir": Path(directory), "location": location, "local": local,
                             "manifest": load(directory), "targets": set()}
        return packages[key]

    for directory in local_dirs:
        package(Path(directory).name.lower(), directory, "", True)
    by_url = {url_key(location): identity for identity, (_, location) in remote.items()}

    def dependency_package(owner, name):
        """A package the owner's manifest depends on, by identity or name."""
        for dep in owner["manifest"].get("dependencies", []):
            kind, = dep
            info = dep[kind][0]
            identity = info.get("identity", "").lower()
            names = {identity, (info.get("nameForTargetDependencyResolutionOnly") or "").lower()}
            if name.lower() not in names:
                continue
            if kind == "fileSystem":
                path = Path(info["path"])
                return package(path.name.lower(), path, "", True)
            if identity not in remote:
                fail(f"{identity} is a dependency the build did not resolve")
            directory, location = remote[identity]
            return package(identity, directory, location, False)
        fail(f"{name} is not a dependency of {owner['dir']}")

    def visit_product(owner, product_name):
        products = [p for p in owner["manifest"].get("products", []) if p["name"] == product_name]
        if not products:
            fail(f"no product {product_name} in {owner['dir']}")
        for target in products[0]["targets"]:
            visit_target(owner, target)

    def visit_target(owner, target_name):
        targets = [t for t in owner["manifest"].get("targets", []) if t["name"] == target_name]
        if not targets:
            fail(f"no target {target_name} in {owner['dir']}")
        target = targets[0]
        if target["type"] in NOT_LINKED or target_name in owner["targets"]:
            return
        owner["targets"].add(target_name)
        for dep in target.get("dependencies") or []:
            kind, = dep
            value = dep[kind]
            if kind == "product":
                name, package_name, _, condition = value
                if applies_to_ios(condition):
                    visit_product(dependency_package(owner, package_name), name)
            elif kind in ("target", "byName"):
                name, condition = value
                if not applies_to_ios(condition):
                    continue
                local_names = {t["name"] for t in owner["manifest"].get("targets", [])}
                if kind == "target" or name in local_names:
                    visit_target(owner, name)
                else:
                    visit_product(dependency_package(owner, name), name)

    for product_name, url in direct:
        if url:
            identity = by_url.get(url_key(url))
            if identity is None:
                fail(f"the app target uses {url}, which the build did not resolve")
            directory, location = remote[identity]
            visit_product(package(identity, directory, location, False), product_name)
            continue
        owners = [p for p in packages.values() if p["local"] and
                  any(prod["name"] == product_name for prod in p["manifest"].get("products", []))]
        if len(owners) != 1:
            fail(f"no single local package has the product {product_name}")
        visit_product(owners[0], product_name)
    return {key: value for key, value in packages.items() if value["targets"]}


# MARK: The checks

def linked_targets(link_files, built_products):
    """Package targets the linker read: the .o files in the built products folder."""
    built = Path(built_products).resolve()
    targets = set()
    for path in link_files:
        try:
            lines = Path(path).read_text(encoding="utf-8").splitlines()
        except OSError as exc:
            fail(f"cannot read the link file list {path}: {exc}")
        for line in lines:
            item = Path(line.strip())
            if item.suffix == ".o" and item.parent.resolve() == built:
                targets.add(item.stem)
    return targets


def check_linked(linked, packages):
    """Every package target the app links is covered by the notices."""
    covered = set()
    for info in packages.values():
        for target in info["targets"]:
            covered |= {target, target.replace("-", "_")}
    missing = sorted(t for t in linked if t not in covered)
    if missing:
        fail(f"the app links {', '.join(missing)}, which the notices do not cover")


def notices_text(packages, pins):
    """The notices for the third-party packages; local packages are zzThat's own."""
    by_identity = {pin.get("identity", "").lower(): pin for pin in pins}
    sections = []
    for key, info in packages.items():
        if info["local"]:
            continue
        pin = by_identity.get(key)
        if pin is None:
            fail(f"{key} is linked but not pinned in the lock")
        state = pin.get("state", {})
        version = state.get("version") or state.get("revision")
        files = sorted(p for p in info["dir"].iterdir() if p.is_file())
        licenses = [p for p in files if LICENSE_NAME.match(p.name)]
        if not licenses:
            fail(f"{key} {version} has no license file in {info['dir']}")
        notices = [p for p in files if NOTICE_NAME.match(p.name)]
        name = info["manifest"].get("name") or key
        parts = [f"{name} {version}\n{pin.get('location', info['location'])}"]
        for path in licenses + notices:
            try:
                parts.append(path.read_text(encoding="utf-8").strip("\n"))
            except (OSError, UnicodeDecodeError) as exc:
                fail(f"cannot read {path}: {exc}")
        sections.append((name.lower(), "\n\n".join(parts)))
    if not sections:
        fail("no third-party package is linked, which cannot be right for this app")
    sections.sort()
    return f"\n\n{SEPARATOR}\n\n".join(text for _, text in sections) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--project", required=True)
    parser.add_argument("--target", required=True)
    parser.add_argument("--resolved", required=True)
    parser.add_argument("--source-packages", required=True)
    parser.add_argument("--built-products", required=True)
    parser.add_argument("--link-file-list", required=True, nargs="+")
    parser.add_argument("--cache", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    try:
        direct, local_dirs = app_products(args.project, args.target)
        remote = remote_packages(args.source_packages)
        # Warm the manifest cache in parallel. A manifest that cannot be read
        # fails the build only if the closure below reaches its package.
        def warm(directory):
            try:
                load_manifest(directory, args.cache)
            except NoticesError:
                pass
        with ThreadPoolExecutor(max_workers=8) as pool:
            list(pool.map(warm, [d for d, _ in remote.values()] + list(local_dirs)))
        packages = closure(direct, local_dirs, remote, lambda d: load_manifest(d, args.cache))
        check_linked(linked_targets(args.link_file_list, args.built_products), packages)
        text = notices_text(packages, read_json(args.resolved).get("pins", []))
    except NoticesError as exc:
        sys.exit(f"error: third-party notices: {exc}")
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    listed = sum(1 for info in packages.values() if not info["local"])
    print(f"third-party notices: {listed} linked packages -> {out}")


if __name__ == "__main__":
    main()
