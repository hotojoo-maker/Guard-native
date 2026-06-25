#!/usr/bin/env python3
"""Single source of truth for the Guard Native KDF (Python side).

Both gen_registry_cipher.py (registry key) and gen_bootstrap_cipher.py (cert-only
bootstrap key) used to carry their OWN inline copy of the scattered segments, the
cert hash, rotl8 and the derive routine. That was three drift-prone copies of the
most expensive-to-get-wrong code in the project (F-31 territory: one byte off ->
正版机 registry 散沙, silent).

This module collapses the Python side to ONE copy:
  * gen_registry_cipher.py   imports derive_registry_key
  * gen_bootstrap_cipher.py  imports derive_bootstrap_key
  * gen_kdf_vectors.py        imports both to emit the C++ self-test vectors

These functions MUST stay byte-for-byte identical to guard::derive_registry_key()
and guard::derive_bootstrap_key() in native_core/src/config_crypto.cpp. The KDF
vector cross-check (tools/gen_kdf_vectors.py -> native_core/src/kdf_vectors.inc ->
guard::kdf_self_test()) exists precisely to catch any divergence at build/CI time.

This module has NO top-level side effects, so it is safe to import.
"""

# Scattered key segments. MUST match config_crypto.cpp seg_a/seg_b/seg_c.
SEG_A = bytes([
    0x3f, 0xa1, 0x08, 0xd4, 0x77, 0x1c, 0xe9, 0x52,
    0x8b, 0x60, 0xbd, 0x14, 0xc6, 0x2a, 0x9f, 0x73,
])
SEG_B = bytes([
    0x5e, 0x02, 0xab, 0x6d, 0xf1, 0x37, 0x80, 0xc4,
    0x19, 0xae, 0x4b, 0xd2, 0x66, 0x8f, 0x33, 0xe7,
])
SEG_C = bytes([
    0x11, 0x9c, 0x4d, 0x70, 0x23, 0xba, 0x5f, 0x06,
    0xe1, 0x38, 0x7a, 0xcd, 0x90, 0x42, 0xfb, 0x85,
])

# Domain-separation tag — bootstrap key only. MUST match config_crypto.cpp _DOM /
# the dom[16] in guard::derive_bootstrap_key().
DOM = bytes([
    0x9a, 0x47, 0xe1, 0x05, 0x3c, 0xb8, 0x6f, 0xd2,
    0x14, 0x8e, 0x7b, 0xa3, 0x50, 0xc9, 0x2d, 0xf6,
])

# Fixed keystore cert SHA-256 (signing/guard-native-debug.keystore, alias
# androiddebugkey). The runtime cert pushed via NativeBridge.setBindingMaterial()
# MUST equal this for the embedded registry/bootstrap blobs to decrypt. Regenerate
# with keytool if the signing keystore changes (see gen_registry_cipher.py).
CERT_SHA256 = bytes([
    0xca, 0x42, 0x1e, 0xc3, 0xa3, 0x37, 0x08, 0xce,
    0xb3, 0xf7, 0x0c, 0x37, 0xf4, 0x61, 0x67, 0x51,
    0x09, 0x47, 0x36, 0xc4, 0x96, 0xfe, 0x21, 0xb3,
    0xb0, 0xe2, 0xce, 0xa4, 0x80, 0xcd, 0xb6, 0xa0,
])


def rotl8(x, r):
    r &= 7
    if r == 0:
        return x
    return ((x << r) | (x >> (8 - r))) & 0xff


def derive_registry_key(binding=b"", server_seed=b""):
    """Mirror guard::derive_registry_key() byte-for-byte.

    binding     = signing-cert SHA-256 (A-step2 anti-repackage fold). C++ only
                  folds it when g_binding_len > 0; pass b"" to skip (A-step1).
    server_seed = S_rel (S3a prod_server_lock fold). Pass b"" for cert-only.
    """
    out = bytearray(16)
    blen = len(binding)
    slen = len(server_seed)
    for i in range(16):
        t = SEG_A[i] ^ SEG_B[(i * 5 + 3) & 15]
        t = rotl8(t, (i % 7) + 1)
        t ^= SEG_C[i]
        t = (t + i * 37) & 0xff
        if blen > 0:
            t ^= binding[(i * 2) % blen]
            t = rotl8(t, binding[(i * 2 + 1) % blen] & 7)
            t ^= binding[(i + 7) % blen]
        if slen > 0:
            t ^= server_seed[(i * 3) % slen]
            t = rotl8(t, server_seed[(i * 3 + 1) % slen] & 7)
            t ^= server_seed[(i + 11) % slen]
        out[i] = t
    return bytes(out)


def derive_bootstrap_key(binding=b""):
    """Mirror guard::derive_bootstrap_key() byte-for-byte.

    Same segments + cert binding as the registry key, but NEVER folds the server
    seed and folds a fixed domain tag so it differs from the registry cert-only
    key.
    """
    out = bytearray(16)
    blen = len(binding)
    for i in range(16):
        t = SEG_A[i] ^ SEG_B[(i * 5 + 3) & 15]
        t = rotl8(t, (i % 7) + 1)
        t ^= SEG_C[i]
        t = (t + i * 37) & 0xff
        if blen > 0:
            t ^= binding[(i * 2) % blen]
            t = rotl8(t, binding[(i * 2 + 1) % blen] & 7)
            t ^= binding[(i + 7) % blen]
        t ^= DOM[i]
        t = rotl8(t, DOM[(i * 7 + 1) & 15] & 7)
        out[i] = t
    return bytes(out)
