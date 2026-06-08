#!/usr/bin/env python3
"""Generate Phase 1A AES-GCM test vectors for config_crypto.cpp."""
import binascii

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    print("ERROR: pip install cryptography")
    raise SystemExit(1)

key = bytes([
    0x67, 0x75, 0x61, 0x72, 0x64, 0x5f, 0x70, 0x31,
    0x61, 0x5f, 0x6b, 0x65, 0x79, 0x21, 0x00, 0x00,
])
nonce = bytes([
    0x67, 0x75, 0x61, 0x72, 0x64, 0x6e, 0x6f, 0x6e,
    0x63, 0x65, 0x30, 0x31,
])
pt = (
    b'{"schema_id":"test_r8071","entries":{"conv.adapter_71":{"class":"kc5.v0"}}}'
)
blob = AESGCM(key).encrypt(nonce, pt, None)
ct, tag = blob[:-16], blob[-16:]
print("PROJECT ct hex:", binascii.hexlify(ct).decode())
print("PROJECT tag hex:", binascii.hexlify(tag).decode())
print("PROJECT ct bytes:", ", ".join("0x%02x" % b for b in ct))
print("PROJECT tag bytes:", ", ".join("0x%02x" % b for b in tag))

nist_tag = AESGCM(bytes(16)).encrypt(bytes(12), b"", None)
print("NIST tag hex:", binascii.hexlify(nist_tag).decode())
