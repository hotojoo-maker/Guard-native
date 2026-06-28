#!/usr/bin/env python3
"""Phase 1C: encrypt native_core/registry_8071.json into an AES-GCM blob and
emit a generated C include (native_core/src/registry_cipher.inc) consumed by
registry_loader.cpp.

Single source of truth = registry_8071.json. Re-run this after editing it; the
.inc is a build artifact, never hand-edited (kills the plaintext/embedded drift).

Phase 1D-local: the AES key is NO LONGER emitted into the .inc. It is DERIVED
from scattered in-SO segments via derive_registry_key() below, which MUST stay
byte-for-byte identical to guard::derive_registry_key() in
native_core/src/config_crypto.cpp. The nonce is randomized per build (stored in
the .inc) to avoid GCM nonce reuse across builds.

A-step2 (anti-repackage): the derivation also folds in the module signing-cert
SHA-256 (kdf_common.CERT_SHA256). At runtime Java reads the installed module's
cert and pushes it via NativeBridge.setBindingMaterial(); a re-signed /
repackaged APK has a different cert → wrong key → scatter. kdf_common.CERT_SHA256
MUST equal the fixed keystore's cert SHA-256. Regenerate it with:
    keytool -list -v -keystore signing/guard-native-debug.keystore \
        -storepass android -alias androiddebugkey   (read the SHA256: line)

Phase 1D-server (later) folds server-derived key material into the derivation
(skill: server material must participate in final key derivation; no server
material → no real registry). Local material only raises the static-analysis bar.
"""
import argparse
import base64
import json
import os

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    print("ERROR: pip install cryptography")
    raise SystemExit(1)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "native_core", "registry_8071.json")
OUT = os.path.join(ROOT, "native_core", "src", "registry_cipher.inc")

MODE_DEV_CERT_ONLY = "dev_cert_only"
MODE_PROD_SERVER_LOCK = "prod_server_lock"

# Phase 1D-local: the scattered key segments, cert binding, rotl8 and derive
# routine live in ONE place now — tools/kdf_common.py — so this generator, the
# bootstrap generator and the KDF self-test vectors can never drift apart.
# kdf_common.derive_registry_key MUST stay byte-for-byte identical to
# guard::derive_registry_key() in native_core/src/config_crypto.cpp; the KDF
# vector cross-check (gen_kdf_vectors.py + guard::kdf_self_test) enforces it.
from kdf_common import CERT_SHA256 as _DEFAULT_CERT_SHA256, derive_registry_key as _kdf_derive_registry_key

# Active cert for this invocation (overridable via --cert-sha256 for coexist line).
_ACTIVE_CERT: bytes = _DEFAULT_CERT_SHA256


def derive_registry_key(server_seed=b""):
    # A-step2: the fixed keystore cert SHA-256 is always folded as binding here
    # (blen=32); runtime Java pushes the same cert via setBindingMaterial().
    return _kdf_derive_registry_key(_ACTIVE_CERT, server_seed)


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generate encrypted registry_cipher.inc from registry_8071.json."
    )
    parser.add_argument(
        "--mode",
        choices=[MODE_DEV_CERT_ONLY, MODE_PROD_SERVER_LOCK],
        default=os.environ.get("GUARD_REGISTRY_MODE", MODE_DEV_CERT_ONLY),
        help=(
            "dev_cert_only preserves current cert-bound local behavior; "
            "prod_server_lock requires a 32-byte GUARD_S_REL_B64 server seed."
        ),
    )
    parser.add_argument(
        "--server-seed-b64",
        default=os.environ.get("GUARD_S_REL_B64", ""),
        help="Base64-encoded 32-byte S_rel. Required in prod_server_lock mode.",
    )
    parser.add_argument(
        "--recipe",
        default=os.environ.get("GUARD_RELEASE_RECIPE", ""),
        help=(
            "Path to a per-release recipe JSON (release/secrets/<release_id>.json). "
            "When set, registry_mode and s_rel_b64 are read from it (single source of "
            "truth shared with miyou-server). See docs/RELEASE_RECIPE契约.md."
        ),
    )
    parser.add_argument(
        "--cert-sha256",
        default="",
        help=(
            "Override signing-cert SHA-256 (hex, 64 chars). Defaults to kdf_common.CERT_SHA256 "
            "(official line). Pass the coexist keystore cert here for the coexist registry. "
            "Also controls --out-file default basename when --release-id is set."
        ),
    )
    parser.add_argument(
        "--out",
        default="",
        help="Output path for the .inc file (default: native_core/src/registry_cipher.inc).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate mode/seed inputs and encrypt in memory without writing registry_cipher.inc.",
    )
    args = parser.parse_args()
    if args.recipe:
        _apply_recipe(args)
    return args


def _apply_recipe(args):
    """Load registry_mode + s_rel_b64 from the per-release recipe JSON. The recipe
    is the single source of truth shared with miyou-server (docs/RELEASE_RECIPE契约.md);
    explicit --mode / --server-seed-b64 must not contradict it."""
    try:
        with open(args.recipe, "r", encoding="utf-8") as f:
            recipe = json.load(f)
    except Exception as exc:
        raise SystemExit("ERROR: cannot read --recipe %s: %s" % (args.recipe, exc))
    mode = recipe.get("registry_mode", "")
    if mode not in (MODE_DEV_CERT_ONLY, MODE_PROD_SERVER_LOCK):
        raise SystemExit("ERROR: recipe registry_mode must be dev_cert_only/prod_server_lock, got %r" % mode)
    s_rel = recipe.get("s_rel_b64", "")
    # Recipe wins; warn-by-erroring if CLI explicitly set a conflicting mode.
    if args.mode != MODE_DEV_CERT_ONLY and args.mode != mode:
        raise SystemExit("ERROR: --mode %s conflicts with recipe registry_mode %s" % (args.mode, mode))
    args.mode = mode
    if mode == MODE_PROD_SERVER_LOCK and not s_rel:
        raise SystemExit("ERROR: recipe %s is prod_server_lock but has no s_rel_b64" % args.recipe)
    args.server_seed_b64 = s_rel
    print("recipe_loaded:", recipe.get("release_id", "?"), "mode:", mode)


def load_server_seed(args):
    if not args.server_seed_b64:
        if args.mode == MODE_PROD_SERVER_LOCK:
            raise SystemExit("ERROR: prod_server_lock requires GUARD_S_REL_B64 / --server-seed-b64")
        return b""
    try:
        seed = base64.b64decode(args.server_seed_b64, validate=True)
    except Exception as exc:
        raise SystemExit("ERROR: invalid server seed base64: %s" % exc)
    if len(seed) != 32:
        raise SystemExit("ERROR: S_rel must be exactly 32 bytes, got %d" % len(seed))
    return seed


args = parse_args()

# Apply --cert-sha256 override (coexist line uses a different keystore).
if args.cert_sha256:
    import binascii as _binascii
    _cert_bytes = _binascii.unhexlify(args.cert_sha256)
    if len(_cert_bytes) != 32:
        raise SystemExit("ERROR: --cert-sha256 must be 64 hex chars (32 bytes)")
    _ACTIVE_CERT = _cert_bytes  # noqa: F811 override module global

# Apply --out override.
if args.out:
    OUT = args.out  # noqa: F811

_S_REL = load_server_seed(args)
requires_server_seed = args.mode == MODE_PROD_SERVER_LOCK
key = derive_registry_key(_S_REL)
print("registry_mode:", args.mode)
print("server_seed_required:", requires_server_seed)
print("server_seed_folded:", len(_S_REL) > 0)
nonce = os.urandom(12)  # random per build → no GCM nonce reuse across builds

with open(SRC, "r", encoding="utf-8") as f:
    data = json.load(f)

# Strip top-level documentation keys (leading underscore) and minify.
data = {k: v for k, v in data.items() if not k.startswith("_")}
pt = json.dumps(data, separators=(",", ":"), ensure_ascii=False).encode("utf-8")

blob = AESGCM(key).encrypt(nonce, pt, None)
ct, tag = blob[:-16], blob[-16:]


def carr(name, b):
    body = ", ".join("0x%02x" % x for x in b)
    return "constexpr uint8_t %s[] = { %s };\n" % (name, body)


lines = [
    "// GENERATED by tools/gen_registry_cipher.py - DO NOT EDIT BY HAND.\n",
    "// Source of truth: native_core/registry_8071.json\n",
    "// Phase 1D-local: NO key constant here. The AES key is derived in-SO by\n",
    "// guard::derive_registry_key() (config_crypto.cpp). Nonce is random per build.\n",
    "#define GUARD_REGISTRY_REQUIRES_SERVER_SEED %d\n" % (1 if requires_server_seed else 0),
    carr("kRegistryNonce", nonce),
    carr("kRegistryCipher", ct),
    carr("kRegistryTag", tag),
]
if args.dry_run:
    print("dry_run: true")
else:
    with open(OUT, "w", encoding="utf-8") as f:
        f.writelines(lines)
    print("wrote", OUT)

print("pt_len", len(pt), "ct_len", len(ct))
print("schema entries:", list(data.get("entries", {}).keys()))
