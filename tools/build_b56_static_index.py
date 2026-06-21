from __future__ import annotations

import re
import json
import zipfile
from pathlib import Path


PROJECT_FILE = Path(r"C:\Users\Me\Desktop\guard_native\jadx_8071_out\classes9.dex.jadx")
ROOT = Path(r"C:\Users\Me\Desktop\guard_native\jadx_8071_out\classes9.dex.jadx")
OUT = Path(r"C:\Users\Me\Desktop\guard_native\tools\b56_static_index")
APK = OUT / "base_8071.apk"

ANCHORS = [
    "1934848488",
    "0x735371e8",
    "WCProbe",
    "normsg",
    "toProtoBuf",
    "dispatchEncryptJNIFuncCall",
    "mmbizresortbuffer",
    "tlcolumn_hottopic",
    "com.tencent.mm.plugin.normsg.f",
    "com.tencent.mm.plugin.normsg.u",
    "com.tencent.mm.normsg.c$p",
]

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;")
CLASS_RE = re.compile(r"\b(class|interface|enum)\s+([A-Za-z_$][\w$]*)")
METHOD_RE = re.compile(
    r"^\s*(?:public|private|protected|static|final|synchronized|native|abstract|strictfp|\s)+"
    r"[\w$<>\[\].?,\s]+\s+([A-Za-z_$][\w$<>]*)\s*\(([^;{}]*)\)"
)
STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
INT_RE = re.compile(r"(?<![\w$])(?:0x[0-9a-fA-F]+|\d{6,})(?![\w$])")
PRINTABLE_RE = re.compile(rb"[\x20-\x7e]{4,}")
CLASS_DESC_RE = re.compile(r"L[a-zA-Z0-9_/$]+;")


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def write_rows(path: Path, rows: list[tuple[str, ...]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for row in rows:
            f.write("\t".join(str(x).replace("\t", " ") for x in row) + "\n")


def dex_fallback_index() -> None:
    if not APK.exists():
        raise SystemExit(f"no jadx java tree and apk not found: {APK}")

    dex_strings: list[tuple[str, ...]] = [("dex", "offset", "string")]
    dex_classes: list[tuple[str, ...]] = [("class", "dex", "offset")]
    strings: list[tuple[str, ...]] = [("literal", "dex", "offset")]
    constants: list[tuple[str, ...]] = [("constant", "dex", "offset", "encoding")]
    invokes: list[tuple[str, ...]] = [("anchor", "dex", "offset", "context")]

    target_int = 1934848488
    le = target_int.to_bytes(4, "little", signed=False)
    be = target_int.to_bytes(4, "big", signed=False)

    with zipfile.ZipFile(APK, "r") as zf:
        dex_names = sorted(name for name in zf.namelist() if name.endswith(".dex"))
        for dex_name in dex_names:
            data = zf.read(dex_name)

            start = 0
            while True:
                idx = data.find(le, start)
                if idx < 0:
                    break
                constants.append(("1934848488", dex_name, hex(idx), "little-endian"))
                start = idx + 1

            start = 0
            while True:
                idx = data.find(be, start)
                if idx < 0:
                    break
                constants.append(("1934848488", dex_name, hex(idx), "big-endian"))
                start = idx + 1

            for match in PRINTABLE_RE.finditer(data):
                raw = match.group(0)
                try:
                    text = raw.decode("utf-8", errors="ignore")
                except Exception:
                    continue
                if not text:
                    continue

                for cls_match in CLASS_DESC_RE.finditer(text):
                    cls = cls_match.group(0)[1:-1].replace("/", ".")
                    dex_classes.append((cls, dex_name, hex(match.start() + cls_match.start())))

                lower_text = text.lower()
                if any(anchor.lower() in lower_text for anchor in ANCHORS):
                    dex_strings.append((dex_name, hex(match.start()), text[:700]))
                    strings.append((text[:700], dex_name, hex(match.start())))
                    for anchor in ANCHORS:
                        if anchor.lower() in lower_text:
                            invokes.append((anchor, dex_name, hex(match.start()), text[:700]))

    write_rows(OUT / "dex_strings.tsv", dex_strings)
    write_rows(OUT / "dex_classes.tsv", dex_classes)
    write_rows(OUT / "strings.tsv", strings)
    write_rows(OUT / "constants.tsv", constants)
    write_rows(OUT / "invokes.tsv", invokes)
    write_rows(OUT / "classes.tsv", [("class", "kind", "file", "line")] + [(r[0], "dex", r[1], r[2]) for r in dex_classes[1:]])
    write_rows(OUT / "methods.tsv", [("method", "file", "line", "signature")])
    write_rows(OUT / "native_methods.tsv", [("method", "file", "line", "signature")])

    (OUT / "anchors.md").write_text(
        "\n".join(
            [
                "# B56 Static Index Anchors",
                "",
                f"APK: `{APK}`",
                "",
                "Mode: raw APK/dex fallback. JADX source tree was not available.",
                "",
                "Dynamic L1 anchors:",
                "",
                "- `u.z3 -> ql3.s.z3 -> w15.kg.toProtoBuf -> modelbase.p2.B2 -> modelbase.t2.U8 -> network.w0.onTransact`",
                "- `u.uc -> ql3.s.h -> w15.kg.toProtoBuf -> modelbase.p2.B2 -> modelbase.t2.U8 -> network.w0.onTransact`",
                "- `plugin.normsg.f.run -> WCProbe.m -> c$p.af`",
                "",
                "Index files:",
                "",
                "- `dex_classes.tsv`",
                "- `dex_strings.tsv`",
                "- `strings.tsv`",
                "- `constants.tsv`",
                "- `invokes.tsv`",
                "",
                "Limitations:",
                "",
                "- This fallback does not produce invoke call graph or Java line numbers.",
                "- Add JADX source later to generate `methods.tsv`, `native_methods.tsv`, and true call slices.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    print("mode=apk-dex-fallback")
    print(f"dex_classes={len(dex_classes) - 1}")
    print(f"dex_strings={len(dex_strings) - 1}")
    print(f"constants={len(constants) - 1}")
    print(f"out={OUT}")


def main() -> None:
    global ROOT
    if PROJECT_FILE.exists() and PROJECT_FILE.is_file():
        try:
            project = json.loads(PROJECT_FILE.read_text(encoding="utf-8"))
            cache_dir = project.get("cacheDir")
            if cache_dir:
                ROOT = Path(cache_dir)
        except Exception as exc:
            print(f"warn: could not parse jadx project file: {exc}")

    if not ROOT.exists():
        dex_fallback_index()
        return

    classes: list[tuple[str, ...]] = [("class", "kind", "file", "line")]
    methods: list[tuple[str, ...]] = [("method", "file", "line", "signature")]
    strings: list[tuple[str, ...]] = [("literal", "file", "line")]
    constants: list[tuple[str, ...]] = [("constant", "file", "line", "context")]
    invokes: list[tuple[str, ...]] = [("anchor", "file", "line", "context")]
    native_methods: list[tuple[str, ...]] = [("method", "file", "line", "signature")]

    java_files = sorted(ROOT.rglob("*.java"))
    if not java_files:
        dex_fallback_index()
        return
    for file in java_files:
        current_package = ""
        current_class = ""
        try:
            lines = file.read_text(encoding="utf-8", errors="ignore").splitlines()
        except OSError:
            continue

        for line_no, line in enumerate(lines, 1):
            package_match = PACKAGE_RE.match(line)
            if package_match:
                current_package = package_match.group(1)

            class_match = CLASS_RE.search(line)
            if class_match:
                current_class = class_match.group(2)
                fqcn = f"{current_package}.{current_class}" if current_package else current_class
                classes.append((fqcn, class_match.group(1), rel(file), str(line_no)))

            method_match = METHOD_RE.match(line)
            if method_match and current_class:
                method_name = method_match.group(1)
                owner = f"{current_package}.{current_class}" if current_package else current_class
                signature = line.strip()[:500]
                methods.append((f"{owner}.{method_name}", rel(file), str(line_no), signature))
                if " native " in f" {line} ":
                    native_methods.append((f"{owner}.{method_name}", rel(file), str(line_no), signature))

            lower_line = line.lower()
            for anchor in ANCHORS:
                if anchor.lower() in lower_line:
                    invokes.append((anchor, rel(file), str(line_no), line.strip()[:700]))

            for literal in STRING_RE.findall(line):
                if any(anchor.lower() in literal.lower() for anchor in ANCHORS):
                    strings.append((literal[:700], rel(file), str(line_no)))

            for const in INT_RE.findall(line):
                if const == "1934848488" or const.lower() == "0x735371e8":
                    constants.append((const, rel(file), str(line_no), line.strip()[:700]))

    write_rows(OUT / "classes.tsv", classes)
    write_rows(OUT / "methods.tsv", methods)
    write_rows(OUT / "strings.tsv", strings)
    write_rows(OUT / "constants.tsv", constants)
    write_rows(OUT / "invokes.tsv", invokes)
    write_rows(OUT / "native_methods.tsv", native_methods)

    anchors_md = OUT / "anchors.md"
    anchors_md.write_text(
        "\n".join(
            [
                "# B56 Static Index Anchors",
                "",
                f"Root: `{ROOT}`",
                "",
                "Dynamic L1 anchors:",
                "",
                "- `u.z3 -> ql3.s.z3 -> w15.kg.toProtoBuf -> modelbase.p2.B2 -> modelbase.t2.U8 -> network.w0.onTransact`",
                "- `u.uc -> ql3.s.h -> w15.kg.toProtoBuf -> modelbase.p2.B2 -> modelbase.t2.U8 -> network.w0.onTransact`",
                "- `plugin.normsg.f.run -> WCProbe.m -> c$p.af`",
                "",
                "Index files:",
                "",
                "- `classes.tsv`",
                "- `methods.tsv`",
                "- `strings.tsv`",
                "- `constants.tsv`",
                "- `invokes.tsv`",
                "- `native_methods.tsv`",
                "",
                "Next read targets:",
                "",
                "- Find `1934848488` / `0x735371e8` references.",
                "- Find `WCProbe` and `c$p` declarations or native bridges.",
                "- Slice around `toProtoBuf`, `modelbase.p2.B2`, `modelbase.t2.U8`, and `network.w0.onTransact`.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    print(f"java_files={len(java_files)}")
    print(f"classes={len(classes) - 1}")
    print(f"methods={len(methods) - 1}")
    print(f"strings={len(strings) - 1}")
    print(f"constants={len(constants) - 1}")
    print(f"invokes={len(invokes) - 1}")
    print(f"native_methods={len(native_methods) - 1}")
    print(f"out={OUT}")


if __name__ == "__main__":
    main()
