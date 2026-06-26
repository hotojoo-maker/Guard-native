// config_crypto.cpp — Phase 1A: AES-128-GCM decrypt + self-test vectors
// Scope: local prototype only. No business hooks, no server, no auth.

#include "guard_core.h"

#include <cstring>
#include <vector>

namespace guard {
namespace {

constexpr char kScatterRegistry[] =
    "{\"schema_id\":\"scatter\",\"wechat_version\":\"0.0.0\",\"entries\":{}}";

constexpr uint8_t kTestKey[16] = {
    0x67, 0x75, 0x61, 0x72, 0x64, 0x5f, 0x70, 0x31,
    0x61, 0x5f, 0x6b, 0x65, 0x79, 0x21, 0x00, 0x00}; // "guard_p1a_key!\0\0"

constexpr uint8_t kTestNonce[12] = {
    0x67, 0x75, 0x61, 0x72, 0x64, 0x6e, 0x6f, 0x6e,
    0x63, 0x65, 0x30, 0x31}; // "guardnonce01"

// Pre-encrypted test registry (tools/gen_gcm_vectors.py)
constexpr uint8_t kTestCiphertext[] = {
    0x81, 0xb6, 0x13, 0x51, 0x73, 0x4d, 0x53, 0x17, 0x3b, 0xe5, 0xec, 0x27,
    0x0a, 0x10, 0xe3, 0x9a, 0xe0, 0xa0, 0x07, 0xac, 0xad, 0x0f, 0xa2, 0x98,
    0x7a, 0x8e, 0xa2, 0xfb, 0x2a, 0x4e, 0xf1, 0x79, 0x35, 0x25, 0x2f, 0x52,
    0xd3, 0xd0, 0x60, 0xb6, 0x5a, 0xae, 0x2b, 0x4f, 0x44, 0x21, 0x2b, 0xd4,
    0x65, 0x37, 0x29, 0x0e, 0xd2, 0x15, 0x75, 0x95, 0x26, 0xd5, 0x4b, 0x7a,
    0x33, 0xfe, 0x97, 0xec, 0x4c, 0xa8, 0x91, 0xf9, 0xa9, 0x89, 0x53, 0xe3,
    0xcc, 0x59, 0x57};

constexpr uint8_t kTestTag[16] = {
    0x16, 0x85, 0x56, 0xad, 0x2d, 0x0b, 0x25, 0xab,
    0x2a, 0xc7, 0x9f, 0x54, 0x34, 0xeb, 0x6a, 0x7b};

// NIST SP 800-38D Case 4 (empty plaintext, cryptography lib reference)
constexpr uint8_t kNistKey[16] = {};
constexpr uint8_t kNistNonce[12] = {};
constexpr uint8_t kNistTag[16] = {
    0x58, 0xe2, 0xfc, 0xce, 0xfa, 0x7e, 0x30, 0x61,
    0x36, 0x7f, 0x1d, 0x57, 0xa4, 0xe7, 0x45, 0x5a};

// ── AES-128 (table-based, decrypt only) ──────────────────────

static const uint8_t sbox[256] = {
    0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
    0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
    0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
    0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
    0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
    0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
    0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
    0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
    0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
    0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
    0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
    0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
    0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
    0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
    0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
    0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16};

static const uint8_t rcon[11] = {0x00,0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36};

static uint8_t xtime(uint8_t x) { return static_cast<uint8_t>((x << 1) ^ ((x & 0x80) ? 0x1b : 0)); }

static uint8_t aes_mul(uint8_t a, uint8_t b) {
    uint8_t result = 0;
    while (b != 0) {
        if ((b & 1) != 0) result ^= a;
        a = xtime(a);
        b = static_cast<uint8_t>(b >> 1);
    }
    return result;
}

static void aes_key_expand(const uint8_t* key, uint8_t rk[176]) {
    std::memcpy(rk, key, 16);
    for (int i = 4; i < 44; ++i) {
        uint8_t t[4];
        std::memcpy(t, rk + (i - 1) * 4, 4);
        if (i % 4 == 0) {
            uint8_t tmp = t[0];
            t[0] = sbox[t[1]] ^ rcon[i / 4];
            t[1] = sbox[t[2]];
            t[2] = sbox[t[3]];
            t[3] = sbox[tmp];
        }
        for (int j = 0; j < 4; ++j) {
            rk[i * 4 + j] = rk[(i - 4) * 4 + j] ^ t[j];
        }
    }
}

static void aes_decrypt_block(const uint8_t rk[176], uint8_t block[16]) {
    static const uint8_t inv_sbox[256] = {
        0x52,0x09,0x6a,0xd5,0x30,0x36,0xa5,0x38,0xbf,0x40,0xa3,0x9e,0x81,0xf3,0xd7,0xfb,
        0x7c,0xe3,0x39,0x82,0x9b,0x2f,0xff,0x87,0x34,0x8e,0x43,0x44,0xc4,0xde,0xe9,0xcb,
        0x54,0x7b,0x94,0x32,0xa6,0xc2,0x23,0x3d,0xee,0x4c,0x95,0x0b,0x42,0xfa,0xc3,0x4e,
        0x08,0x2e,0xa1,0x66,0x28,0xd9,0x24,0xb2,0x76,0x5b,0xa2,0x49,0x6d,0x8b,0xd1,0x25,
        0x72,0xf8,0xf6,0x64,0x86,0x68,0x98,0x16,0xd4,0xa4,0x5c,0xcc,0x5d,0x65,0xb6,0x92,
        0x6c,0x70,0x48,0x50,0xfd,0xed,0xb9,0xda,0x5e,0x15,0x46,0x57,0xa7,0x8d,0x9d,0x84,
        0x90,0xd8,0xab,0x00,0x8c,0xbc,0xd3,0x0a,0xf7,0xe4,0x58,0x05,0xb8,0xb3,0x45,0x06,
        0xd0,0x2c,0x1e,0x8f,0xca,0x3f,0x0f,0x02,0xc1,0xaf,0xbd,0x03,0x01,0x13,0x8a,0x6b,
        0x3a,0x91,0x11,0x41,0x4f,0x67,0xdc,0xea,0x97,0xf2,0xcf,0xce,0xf0,0xb4,0xe6,0x73,
        0x96,0xac,0x74,0x22,0xe7,0xad,0x35,0x85,0xe2,0xf9,0x37,0xe8,0x1c,0x75,0xdf,0x6e,
        0x47,0xf1,0x1a,0x71,0x1d,0x29,0xc5,0x89,0x6f,0xb7,0x62,0x0e,0xaa,0x18,0xbe,0x1b,
        0xfc,0x56,0x3e,0x4b,0xc6,0xd2,0x79,0x20,0x9a,0xdb,0xc0,0xfe,0x78,0xcd,0x5a,0xf4,
        0x1f,0xdd,0xa8,0x33,0x88,0x07,0xc7,0x31,0xb1,0x12,0x10,0x59,0x27,0x80,0xec,0x5f,
        0x60,0x51,0x7f,0xa9,0x19,0xb5,0x4a,0x0d,0x2d,0xe5,0x7a,0x9f,0x93,0xc9,0x9c,0xef,
        0xa0,0xe0,0x3b,0x4d,0xae,0x2a,0xf5,0xb0,0xc8,0xeb,0xbb,0x3c,0x83,0x53,0x99,0x61,
        0x17,0x2b,0x04,0x7e,0xba,0x77,0xd6,0x26,0xe1,0x69,0x14,0x63,0x55,0x21,0x0c,0x7d};

    auto add_round_key = [&](int round) {
        for (int i = 0; i < 16; ++i) block[i] ^= rk[round * 16 + i];
    };
    auto inv_sub_bytes = [&]() {
        for (int i = 0; i < 16; ++i) block[i] = inv_sbox[block[i]];
    };
    auto inv_shift_rows = [&]() {
        uint8_t t;
        t=block[13]; block[13]=block[9]; block[9]=block[5]; block[5]=block[1]; block[1]=t;
        t=block[2]; block[2]=block[10]; block[10]=t; t=block[6]; block[6]=block[14]; block[14]=t;
        t=block[3]; block[3]=block[7]; block[7]=block[11]; block[11]=block[15]; block[15]=t;
    };
    auto inv_mix_columns = [&]() {
        for (int c = 0; c < 4; ++c) {
            int base = c * 4;
            uint8_t a[4];
            for (int r = 0; r < 4; ++r) a[r] = block[base + r];
            block[base + 0] = aes_mul(a[0], 14) ^ aes_mul(a[1], 11) ^ aes_mul(a[2], 13) ^ aes_mul(a[3], 9);
            block[base + 1] = aes_mul(a[0], 9) ^ aes_mul(a[1], 14) ^ aes_mul(a[2], 11) ^ aes_mul(a[3], 13);
            block[base + 2] = aes_mul(a[0], 13) ^ aes_mul(a[1], 9) ^ aes_mul(a[2], 14) ^ aes_mul(a[3], 11);
            block[base + 3] = aes_mul(a[0], 11) ^ aes_mul(a[1], 13) ^ aes_mul(a[2], 9) ^ aes_mul(a[3], 14);
        }
    };

    add_round_key(10);
    inv_shift_rows(); inv_sub_bytes(); add_round_key(9);
    for (int round = 8; round >= 1; --round) {
        inv_mix_columns(); inv_shift_rows(); inv_sub_bytes(); add_round_key(round);
    }
    inv_shift_rows(); inv_sub_bytes(); add_round_key(0);
}

static void aes_encrypt_block(const uint8_t rk[176], uint8_t block[16]) {
    auto add_round_key = [&](int round) {
        for (int i = 0; i < 16; ++i) block[i] ^= rk[round * 16 + i];
    };
    auto sub_bytes = [&]() {
        for (int i = 0; i < 16; ++i) block[i] = sbox[block[i]];
    };
    auto shift_rows = [&]() {
        uint8_t t;
        t=block[1]; block[1]=block[5]; block[5]=block[9]; block[9]=block[13]; block[13]=t;
        t=block[2]; block[2]=block[10]; block[10]=t; t=block[6]; block[6]=block[14]; block[14]=t;
        t=block[15]; block[15]=block[11]; block[11]=block[7]; block[7]=block[3]; block[3]=t;
    };
    auto mix_columns = [&]() {
        for (int c = 0; c < 4; ++c) {
            int base = c * 4;
            uint8_t a[4];
            for (int r = 0; r < 4; ++r) a[r] = block[base + r];
            block[base + 0] = aes_mul(a[0], 2) ^ aes_mul(a[1], 3) ^ a[2] ^ a[3];
            block[base + 1] = a[0] ^ aes_mul(a[1], 2) ^ aes_mul(a[2], 3) ^ a[3];
            block[base + 2] = a[0] ^ a[1] ^ aes_mul(a[2], 2) ^ aes_mul(a[3], 3);
            block[base + 3] = aes_mul(a[0], 3) ^ a[1] ^ a[2] ^ aes_mul(a[3], 2);
        }
    };

    add_round_key(0);
    for (int round = 1; round <= 9; ++round) {
        sub_bytes(); shift_rows(); mix_columns(); add_round_key(round);
    }
    sub_bytes(); shift_rows(); add_round_key(10);
}

static void aes_ecb_encrypt(const uint8_t* key, const uint8_t* in, uint8_t* out) {
    uint8_t rk[176];
    aes_key_expand(key, rk);
    uint8_t block[16];
    std::memcpy(block, in, 16);
    aes_encrypt_block(rk, block);
    std::memcpy(out, block, 16);
}

static void aes_ecb_decrypt(const uint8_t* key, const uint8_t* in, uint8_t* out) {
    uint8_t rk[176];
    aes_key_expand(key, rk);
    uint8_t block[16];
    std::memcpy(block, in, 16);
    aes_decrypt_block(rk, block);
    std::memcpy(out, block, 16);
}

// ── GCM (96-bit IV, no AAD) ──────────────────────────────────

static void gf128_mul(uint8_t x[16], const uint8_t h[16]) {
    uint8_t z[16] = {};
    uint8_t v[16];
    std::memcpy(v, h, 16);
    for (int i = 0; i < 128; ++i) {
        if (x[i / 8] & static_cast<uint8_t>(0x80 >> (i % 8))) {
            for (int j = 0; j < 16; ++j) z[j] ^= v[j];
        }
        uint8_t lsb = static_cast<uint8_t>(v[15] & 1);
        for (int j = 15; j > 0; --j) {
            v[j] = static_cast<uint8_t>((v[j] >> 1) | ((v[j - 1] & 1) << 7));
        }
        v[0] = static_cast<uint8_t>((v[0] >> 1) ^ (lsb ? 0xe1 : 0));
    }
    std::memcpy(x, z, 16);
}

static void ghash_update(uint8_t y[16], const uint8_t h[16], const uint8_t* data, size_t len) {
    for (size_t off = 0; off < len; off += 16) {
        uint8_t block[16] = {};
        size_t chunk = (len - off < 16) ? (len - off) : 16;
        std::memcpy(block, data + off, chunk);
        for (int i = 0; i < 16; ++i) y[i] ^= block[i];
        gf128_mul(y, h);
    }
}

static void inc32(uint8_t ctr[16]) {
    for (int i = 15; i >= 12; --i) {
        if (++ctr[i] != 0) break;
    }
}

static bool gcm_encrypt(const uint8_t* key,
                        const uint8_t* nonce, size_t nonce_len,
                        const uint8_t* plaintext, size_t plaintext_len,
                        std::vector<uint8_t>& ciphertext_out,
                        uint8_t tag[16]) {
    if (key == nullptr || nonce == nullptr || tag == nullptr) return false;
    if (nonce_len != 12) return false;

    uint8_t rk[176];
    aes_key_expand(key, rk);

    uint8_t h[16] = {};
    aes_encrypt_block(rk, h);

    uint8_t j0[16] = {};
    std::memcpy(j0, nonce, 12);
    j0[15] = 1;

    ciphertext_out.assign(plaintext_len, 0);
    uint8_t ctr[16];
    std::memcpy(ctr, j0, 16);

    size_t done = 0;
    while (done < plaintext_len) {
        inc32(ctr);
        uint8_t ks[16];
        std::memcpy(ks, ctr, 16);
        aes_encrypt_block(rk, ks);
        size_t chunk = (plaintext_len - done < 16) ? (plaintext_len - done) : 16;
        for (size_t i = 0; i < chunk; ++i) {
            ciphertext_out[done + i] = plaintext[done + i] ^ ks[i];
        }
        done += chunk;
    }

    uint8_t y[16] = {};
    ghash_update(y, h, ciphertext_out.data(), ciphertext_out.size());

    uint8_t len_block[16] = {};
    uint64_t ct_bits = static_cast<uint64_t>(plaintext_len) * 8;
    for (int i = 0; i < 8; ++i) {
        len_block[15 - i] = static_cast<uint8_t>((ct_bits >> (i * 8)) & 0xff);
    }
    ghash_update(y, h, len_block, 16);

    std::memcpy(tag, j0, 16);
    aes_encrypt_block(rk, tag);
    for (int i = 0; i < 16; ++i) tag[i] ^= y[i];
    return true;
}

static bool gcm_decrypt(const uint8_t* key,
                        const uint8_t* nonce, size_t nonce_len,
                        const uint8_t* ciphertext, size_t ciphertext_len,
                        const uint8_t* tag, size_t tag_len,
                        std::vector<uint8_t>& plaintext_out) {
    if (key == nullptr || nonce == nullptr || tag == nullptr) return false;
    if (nonce_len != 12 || tag_len != 16) return false;

    uint8_t rk[176];
    aes_key_expand(key, rk);

    uint8_t h[16] = {};
    aes_encrypt_block(rk, h);

    uint8_t j0[16] = {};
    std::memcpy(j0, nonce, 12);
    j0[15] = 1;

    uint8_t y[16] = {};
    ghash_update(y, h, ciphertext, ciphertext_len);

    uint8_t len_block[16] = {};
    uint64_t ct_bits = static_cast<uint64_t>(ciphertext_len) * 8;
    for (int i = 0; i < 8; ++i) len_block[15 - i] = static_cast<uint8_t>((ct_bits >> (i * 8)) & 0xff);
    ghash_update(y, h, len_block, 16);

    uint8_t expected_tag[16];
    std::memcpy(expected_tag, j0, 16);
    aes_encrypt_block(rk, expected_tag);
    for (int i = 0; i < 16; ++i) expected_tag[i] ^= y[i];

    uint8_t diff = 0;
    for (size_t i = 0; i < 16; ++i) diff |= static_cast<uint8_t>(expected_tag[i] ^ tag[i]);
    if (diff != 0) return false;

    plaintext_out.assign(ciphertext_len, 0);
    uint8_t ctr[16];
    std::memcpy(ctr, j0, 16);

    size_t done = 0;
    while (done < ciphertext_len) {
        inc32(ctr);
        uint8_t ks[16];
        std::memcpy(ks, ctr, 16);
        aes_encrypt_block(rk, ks);
        size_t chunk = (ciphertext_len - done < 16) ? (ciphertext_len - done) : 16;
        for (size_t i = 0; i < chunk; ++i) {
            plaintext_out[done + i] = ciphertext[done + i] ^ ks[i];
        }
        done += chunk;
    }
    return true;
}

static uint8_t rotl8(uint8_t x, int r) {
    r &= 7;
    if (r == 0) return x;
    return static_cast<uint8_t>(((x << r) | (x >> (8 - r))) & 0xff);
}

// Phase 1D-local A-step2: runtime binding material (the module's own signing
// cert SHA-256, pushed down by Java). Folded into the registry key derivation,
// so a re-signed / repackaged APK (different cert) derives a wrong key → scatter.
uint8_t g_binding[32];
size_t  g_binding_len = 0;

// Phase 1D-server (S3a): runtime server seed S_rel, unwrapped from the envelope's
// k field and folded into derive_registry_key. No server seed → registry scatters
// (= real lock: registry only decrypts after a valid server envelope unwraps k).
uint8_t g_server_seed[32];
size_t  g_server_seed_len = 0;

// Phase 1G (牙④ a案 重放/过期绑定): 信封硬过期点 + 可信时间, 由 Java 下推
// (set_envelope_expiry)。unwrap_server_seed 成功路径比 trusted_now >= hard_expire
// → 旧/过期信封重放 → 散沙 (不折静态 key, 避免续约自锁; trusted_now 吊 LeaseClock
// 官方授时 floor 防冻结)。0 = 未下推 → 不做过期检查 (向后兼容 / 不误伤正版)。
uint64_t g_seed_hard_expire = 0;
uint64_t g_seed_trusted_now = 0;

// Phase 1F (牙③ W_dev): per-device material D_mat = SHA-256(ANDROID_ID) full 32B,
// pushed down by Java (set_device_material). Batch 0: stored only — NOT yet wired
// into unwrap_server_seed (Batch 1 double-try). Folded into derive_wrap_key().
// MUST be the real device ANDROID_ID, never the official SSAID A2 feeds the host.
uint8_t g_device_mat[32];
size_t  g_device_mat_len = 0;

// Per-release wrapping key W[:16] — the AES-128 key that decrypts k → S_rel.
// Scattered into two halves (miyou-server config holds the same 16 bytes),
// reassembled only at use.
// [GUARD-TRAP] This is W, NOT S_rel and NOT the envelope HMAC secret — three
// different per-release materials. Do not merge or replace with a literal key.
const uint8_t g_wk_lo[8] = {0x58, 0x18, 0x26, 0xdd, 0xa4, 0xed, 0x49, 0x0d};
const uint8_t g_wk_hi[8] = {0xf5, 0x5b, 0xdb, 0x4d, 0x0c, 0x03, 0x88, 0xe3};

static ConfigDecryptResult make_scatter() {
    return ConfigDecryptResult{false, kScatterRegistry};
}

static bool is_scatter_failure(const ConfigDecryptResult& result) {
    return !result.ok &&
           result.plaintext.find("scatter") != std::string::npos &&
           result.plaintext.find("kc5.v0") == std::string::npos;
}

// 牙④ a案: 信封是否已过硬过期点 (hard_expire = leaseExpire + 7天断网宽限, Java 下推)。
// hard_expire / trusted_now 任一为 0 = 未下推 → 不判过期 (向后兼容、不误伤正版)。
static bool seed_expired() {
    return g_seed_hard_expire > 0 && g_seed_trusted_now > 0
        && g_seed_trusted_now >= g_seed_hard_expire;
}

}  // namespace

ConfigDecryptResult decrypt_config(const uint8_t* key,
                                   size_t key_len,
                                   const uint8_t* nonce,
                                   size_t nonce_len,
                                   const uint8_t* ciphertext,
                                   size_t ciphertext_len,
                                   const uint8_t* tag,
                                   size_t tag_len) {
    if (key == nullptr || nonce == nullptr || tag == nullptr) return make_scatter();
    if (key_len != 16 || nonce_len != 12 || tag_len != 16) return make_scatter();
    if (ciphertext == nullptr && ciphertext_len != 0) return make_scatter();

    std::vector<uint8_t> plain;
    if (!gcm_decrypt(key, nonce, nonce_len,
                     ciphertext ? ciphertext : reinterpret_cast<const uint8_t*>(""),
                     ciphertext_len,
                     tag, tag_len,
                     plain)) {
        return make_scatter();
    }
    return ConfigDecryptResult{true, std::string(plain.begin(), plain.end())};
}

void set_binding_material(const uint8_t* data, size_t len) {
    if (data == nullptr || len == 0) {
        g_binding_len = 0;
        return;
    }
    if (len > sizeof(g_binding)) len = sizeof(g_binding);
    std::memcpy(g_binding, data, len);
    g_binding_len = len;
}

void set_device_material(const uint8_t* data, size_t len) {
    // Phase 1F (牙③ W_dev). Batch 0: store only; not yet read by any unwrap path.
    if (data == nullptr || len == 0) {
        g_device_mat_len = 0;
        return;
    }
    if (len > sizeof(g_device_mat)) len = sizeof(g_device_mat);
    std::memcpy(g_device_mat, data, len);
    g_device_mat_len = len;
}

void clear_server_seed() {
    g_server_seed_len = 0;
}

// 牙④ a案: Java 验签后下推「硬过期点 hard_expire = leaseExpire + 7天断网宽限」+
// 「可信时间 trusted_now」(都 epoch 秒)。unwrap_server_seed 据此拒旧/过期信封。
// 不折静态 registry key (续约不自锁); 传 0 = 关闭过期检查 (不误伤)。
void set_envelope_expiry(uint64_t hard_expire, uint64_t trusted_now) {
    g_seed_hard_expire = hard_expire;
    g_seed_trusted_now = trusted_now;
}

bool server_seed_ready() {
    return g_server_seed_len == 32;
}

bool unwrap_server_seed(const uint8_t* k, size_t k_len,
                        const uint8_t* nonce, size_t nonce_len) {
    // k = ct(32) || tag(16). Decrypt AES-128-GCM(key=W[:16], nonce=n[:12]) → S_rel(32).
    // Batch 1 (牙③ 灰度①双试): try the per-device wrap key W_dev first, then fall back
    // to the global W. The server still ships global-W-wrapped k, so the fallback keeps
    // current 正版 working (backward-compatible); W_dev only succeeds once the server
    // re-wraps per device (Batch 2). Global W stays until Batch 3.
    // Any failure → clear seed (registry scatters = fail-closed, real lock).
    g_server_seed_len = 0;
    if (k == nullptr || nonce == nullptr) return false;
    if (k_len != 48 || nonce_len < 12) return false;
    // 牙④ (a案): 旧/过期信封 (租约到期 + 7天断网宽限耗尽) → 散沙, 不折静态 key。
    // trusted_now 由 Java(LeaseClock 官方授时 floor 防冻结)下推; 未下推(=0)则跳过。
    if (seed_expired()) return false;

    std::vector<uint8_t> seed;

    // ① W_dev (per-device) — only when device material D_mat is loaded.
    if (g_device_mat_len == 32) {
        uint8_t wkd[16];
        derive_wrap_key(wkd);
        if (gcm_decrypt(wkd, nonce, 12, k, 32, k + 32, 16, seed) && seed.size() == 32) {
            std::memcpy(g_server_seed, seed.data(), 32);
            g_server_seed_len = 32;
            return true;
        }
        seed.clear();
    }

    // ② fall back to global W (g_wk_lo||g_wk_hi) — unchanged legacy path (current server).
    uint8_t wk[16];
    std::memcpy(wk, g_wk_lo, 8);
    std::memcpy(wk + 8, g_wk_hi, 8);
    if (gcm_decrypt(wk, nonce, 12, k, 32, k + 32, 16, seed) && seed.size() == 32) {
        std::memcpy(g_server_seed, seed.data(), 32);
        g_server_seed_len = 32;
        return true;
    }
    return false;
}

void derive_registry_key(uint8_t out[16]) {
    // [GUARD-TRAP] Registry key is DERIVED, never a single visible constant.
    // Mirror tools/gen_registry_cipher.py::derive_registry_key() exactly.
    // Three scattered segments + non-linear mix → static analysts must reverse
    // this routine instead of grepping a key array. A-step2 folds in the
    // module signing-cert SHA-256 (set_binding_material): re-signed APK → wrong
    // key → scatter. No binding set → also wrong key (fail closed).
    // See PROTECTION_MAP §4.
    static const uint8_t seg_a[16] = {
        0x3f, 0xa1, 0x08, 0xd4, 0x77, 0x1c, 0xe9, 0x52,
        0x8b, 0x60, 0xbd, 0x14, 0xc6, 0x2a, 0x9f, 0x73};
    static const uint8_t seg_b[16] = {
        0x5e, 0x02, 0xab, 0x6d, 0xf1, 0x37, 0x80, 0xc4,
        0x19, 0xae, 0x4b, 0xd2, 0x66, 0x8f, 0x33, 0xe7};
    static const uint8_t seg_c[16] = {
        0x11, 0x9c, 0x4d, 0x70, 0x23, 0xba, 0x5f, 0x06,
        0xe1, 0x38, 0x7a, 0xcd, 0x90, 0x42, 0xfb, 0x85};
    for (int i = 0; i < 16; ++i) {
        uint8_t t = static_cast<uint8_t>(seg_a[i] ^ seg_b[(i * 5 + 3) & 15]);
        t = rotl8(t, (i % 7) + 1);
        t = static_cast<uint8_t>(t ^ seg_c[i]);
        t = static_cast<uint8_t>(t + i * 37);
        if (g_binding_len > 0) {
            t = static_cast<uint8_t>(t ^ g_binding[(i * 2) % g_binding_len]);
            t = rotl8(t, g_binding[(i * 2 + 1) % g_binding_len] & 7);
            t = static_cast<uint8_t>(t ^ g_binding[(i + 7) % g_binding_len]);
        }
        // Phase 1D-server (S3a): fold the server seed S_rel. MUST mirror
        // gen_registry_cipher.py::derive_registry_key() byte-for-byte.
        // DEV cert-only builds may run with no server seed; PROD server-lock
        // builds are guarded in registry_load_embedded() and scatter before this
        // derivation is used without a valid seed.
        if (server_seed_ready()) {
            t = static_cast<uint8_t>(t ^ g_server_seed[(i * 3) % g_server_seed_len]);
            t = rotl8(t, g_server_seed[(i * 3 + 1) % g_server_seed_len] & 7);
            t = static_cast<uint8_t>(t ^ g_server_seed[(i + 11) % g_server_seed_len]);
        }
        out[i] = t;
    }
}

void derive_bootstrap_key(uint8_t out[16]) {
    // [GUARD-TRAP] C2 cert-only bootstrap key. Mirror
    // tools/gen_bootstrap_cipher.py::derive_bootstrap_key() byte-for-byte.
    // Same scattered segments + cert binding as derive_registry_key(), but:
    //   - NEVER folds the server seed: the AUTH server endpoint list must
    //     decrypt before any server handshake, otherwise prod_server_lock would
    //     deadlock (need server for the seed, need the endpoint to reach the
    //     server). So this blob stays cert-only forever.
    //   - folds a fixed domain-separation tag so the bootstrap key differs from
    //     the registry cert-only key. See docs/HONEYPOT_蜜罐设计.md §4 / PROTECTION_MAP.
    static const uint8_t seg_a[16] = {
        0x3f, 0xa1, 0x08, 0xd4, 0x77, 0x1c, 0xe9, 0x52,
        0x8b, 0x60, 0xbd, 0x14, 0xc6, 0x2a, 0x9f, 0x73};
    static const uint8_t seg_b[16] = {
        0x5e, 0x02, 0xab, 0x6d, 0xf1, 0x37, 0x80, 0xc4,
        0x19, 0xae, 0x4b, 0xd2, 0x66, 0x8f, 0x33, 0xe7};
    static const uint8_t seg_c[16] = {
        0x11, 0x9c, 0x4d, 0x70, 0x23, 0xba, 0x5f, 0x06,
        0xe1, 0x38, 0x7a, 0xcd, 0x90, 0x42, 0xfb, 0x85};
    static const uint8_t dom[16] = {
        0x9a, 0x47, 0xe1, 0x05, 0x3c, 0xb8, 0x6f, 0xd2,
        0x14, 0x8e, 0x7b, 0xa3, 0x50, 0xc9, 0x2d, 0xf6};
    for (int i = 0; i < 16; ++i) {
        uint8_t t = static_cast<uint8_t>(seg_a[i] ^ seg_b[(i * 5 + 3) & 15]);
        t = rotl8(t, (i % 7) + 1);
        t = static_cast<uint8_t>(t ^ seg_c[i]);
        t = static_cast<uint8_t>(t + i * 37);
        if (g_binding_len > 0) {
            t = static_cast<uint8_t>(t ^ g_binding[(i * 2) % g_binding_len]);
            t = rotl8(t, g_binding[(i * 2 + 1) % g_binding_len] & 7);
            t = static_cast<uint8_t>(t ^ g_binding[(i + 7) % g_binding_len]);
        }
        // Domain separation (replaces the server-seed fold of the registry key).
        t = static_cast<uint8_t>(t ^ dom[i]);
        t = rotl8(t, dom[(i * 7 + 1) & 15] & 7);
        out[i] = t;
    }
}

void derive_wrap_key(uint8_t out[16]) {
    // [GUARD-TRAP] 牙③ W_dev (per-device wrapping key). Mirror
    // tools/kdf_common.py::derive_wrap_key() byte-for-byte. Domain-separated
    // wseg_* (MUST differ from derive_registry_key's seg_*) + the device material
    // fold (g_device_mat = SHA-256(ANDROID_ID), set by set_device_material).
    // Batch 0: NOT yet used by unwrap_server_seed (still global W); see
    // 施工提示词_SO黑盒钥匙加固 Batch 1 double-try.
    static const uint8_t wseg_a[16] = {
        0x8a, 0x14, 0xd9, 0x63, 0x2f, 0xb7, 0x4e, 0xc1,
        0x05, 0x9d, 0x76, 0xe2, 0x3b, 0xa8, 0x50, 0xff};
    static const uint8_t wseg_b[16] = {
        0x1d, 0xc7, 0x6a, 0x39, 0x84, 0x0e, 0xf2, 0x5b,
        0xae, 0x47, 0xb0, 0x92, 0x68, 0xd5, 0x21, 0x3c};
    static const uint8_t wseg_c[16] = {
        0xf6, 0x09, 0x7e, 0xa3, 0x4d, 0xc8, 0x1b, 0x60,
        0x95, 0x2a, 0xe7, 0x53, 0x88, 0x31, 0xbc, 0x0f};
    for (int i = 0; i < 16; ++i) {
        uint8_t t = static_cast<uint8_t>(wseg_a[i] ^ wseg_b[(i * 5 + 3) & 15]);
        t = rotl8(t, (i % 7) + 1);
        t = static_cast<uint8_t>(t ^ wseg_c[i]);
        t = static_cast<uint8_t>(t + i * 37);
        if (g_device_mat_len > 0) {
            t = static_cast<uint8_t>(t ^ g_device_mat[(i * 3) % g_device_mat_len]);
            t = rotl8(t, g_device_mat[(i * 3 + 1) % g_device_mat_len] & 7);
            t = static_cast<uint8_t>(t ^ g_device_mat[(i + 11) % g_device_mat_len]);
        }
        out[i] = t;
    }
}

#ifdef GUARD_DEV_SELFTEST
// KDF cross-check vectors (fixed inputs → expected derive_* output), generated
// from tools/kdf_common.py by tools/gen_kdf_vectors.py. Debug-config only
// (GUARD_DEV_SELFTEST, see CMakeLists.txt): never embedded in the release SO.
// NOTE: do not use GUARD_DEBUG — it leaks into release (build.gradle bug).
#include "kdf_vectors.inc"
#endif

bool kdf_self_test() {
#ifdef GUARD_DEV_SELFTEST
    // Cross-check that guard::derive_* match the Python kdf_common output pinned
    // in kdf_vectors.inc. A single divergent byte → registry scatters silently on
    // real devices (F-31); this fails the build-time gate instead.
    // Live key state is saved and restored so calling this never corrupts a
    // running module's derivation inputs.
    uint8_t saved_binding[sizeof(g_binding)];
    std::memcpy(saved_binding, g_binding, sizeof(g_binding));
    size_t saved_binding_len = g_binding_len;
    uint8_t saved_seed[sizeof(g_server_seed)];
    std::memcpy(saved_seed, g_server_seed, sizeof(g_server_seed));
    size_t saved_seed_len = g_server_seed_len;
    uint8_t saved_device[sizeof(g_device_mat)];
    std::memcpy(saved_device, g_device_mat, sizeof(g_device_mat));
    size_t saved_device_len = g_device_mat_len;

    bool ok = true;
    uint8_t k[16];

    // (1) registry key, cert-only (binding set, no server seed).
    std::memcpy(g_binding, kKdfTestBinding, sizeof(g_binding));
    g_binding_len = sizeof(g_binding);
    g_server_seed_len = 0;
    derive_registry_key(k);
    if (std::memcmp(k, kKdfVecRegistryCertOnly, 16) != 0) ok = false;
    uint8_t reg_certonly[16];
    std::memcpy(reg_certonly, k, 16);

    // (2) registry key, cert + server seed (exercises the S_rel fold index math).
    std::memcpy(g_server_seed, kKdfTestSeed, sizeof(g_server_seed));
    g_server_seed_len = sizeof(g_server_seed);
    derive_registry_key(k);
    if (std::memcmp(k, kKdfVecRegistryCertSeed, 16) != 0) ok = false;

    // (3) bootstrap key (cert binding, seed ignored, + domain tag).
    uint8_t b[16];
    derive_bootstrap_key(b);
    if (std::memcmp(b, kKdfVecBootstrap, 16) != 0) ok = false;

    // (4) domain separation: registry cert-only key must differ from bootstrap.
    if (std::memcmp(reg_certonly, b, 16) == 0) ok = false;

    // (5) 牙③ wrap key: device material set, derive_wrap_key matches the vector.
    std::memcpy(g_device_mat, kKdfTestDevice, sizeof(g_device_mat));
    g_device_mat_len = sizeof(g_device_mat);
    uint8_t w[16];
    derive_wrap_key(w);
    if (std::memcmp(w, kKdfVecWrap, 16) != 0) ok = false;

    // (6) 牙③ domain separation: derive_wrap_key(X) must differ from
    // derive_registry_key(cert-only off, server_seed=X) — same fold math, only the
    // base segments differ. Equality ⇒ wseg_* == seg_* (domain separation broken).
    g_binding_len = 0;
    std::memcpy(g_server_seed, kKdfTestDevice, sizeof(g_server_seed));
    g_server_seed_len = sizeof(g_server_seed);
    derive_registry_key(k);
    if (std::memcmp(w, k, 16) == 0) ok = false;

    // Restore live key state.
    std::memcpy(g_binding, saved_binding, sizeof(g_binding));
    g_binding_len = saved_binding_len;
    std::memcpy(g_server_seed, saved_seed, sizeof(g_server_seed));
    g_server_seed_len = saved_seed_len;
    std::memcpy(g_device_mat, saved_device, sizeof(g_device_mat));
    g_device_mat_len = saved_device_len;
    return ok;
#else
    // Release SO: vectors are not embedded; KDF drift is caught in debug/CI
    // (tools/run_native_tests.ps1) before release. No-op here.
    return true;
#endif
}

bool decrypt_config_self_test() {
    // AES-128 block sanity: E(0,0) reference vector
    uint8_t zero_key[16] = {};
    uint8_t zero_block[16] = {};
    uint8_t aes_out[16] = {};
    aes_ecb_encrypt(zero_key, zero_block, aes_out);
    static const uint8_t kAesRef[16] = {
        0x66, 0xe9, 0x4b, 0xd4, 0xef, 0x8a, 0x2c, 0x3b,
        0x88, 0x4c, 0xfa, 0x59, 0xca, 0x34, 0x2b, 0x2e};
    if (std::memcmp(aes_out, kAesRef, 16) != 0) return false;

    // Local encrypt/decrypt roundtrip
    const char* round_pt = "{\"schema_id\":\"roundtrip\"}";
    std::vector<uint8_t> round_ct;
    uint8_t round_tag[16] = {};
    if (!gcm_encrypt(kTestKey, kTestNonce, 12,
                     reinterpret_cast<const uint8_t*>(round_pt), std::strlen(round_pt),
                     round_ct, round_tag)) {
        return false;
    }
    auto round = decrypt_config(kTestKey, 16,
                                kTestNonce, 12,
                                round_ct.data(), round_ct.size(),
                                round_tag, 16);
    if (!round.ok || round.plaintext != round_pt) return false;

    // NIST empty plaintext vector
    auto nist = decrypt_config(kNistKey, 16,
                               kNistNonce, 12,
                               nullptr, 0,
                               kNistTag, 16);
    if (!nist.ok || !nist.plaintext.empty()) return false;

    // Tampered tag must scatter
    uint8_t bad_tag[16];
    std::memcpy(bad_tag, kTestTag, 16);
    bad_tag[0] ^= 0x01;
    auto tamper = decrypt_config(kTestKey, 16,
                                 kTestNonce, 12,
                                 kTestCiphertext, sizeof(kTestCiphertext),
                                 bad_tag, 16);
    if (tamper.ok) return false;
    if (tamper.plaintext.find("kc5.v0") != std::string::npos) return false;

    // Project test vector must decrypt registry JSON
    auto project = decrypt_config(kTestKey, 16,
                                  kTestNonce, 12,
                                  kTestCiphertext, sizeof(kTestCiphertext),
                                  kTestTag, 16);
    if (!project.ok) return false;
    if (project.plaintext.find("test_r8071") == std::string::npos) return false;
    if (project.plaintext.find("kc5.v0") == std::string::npos) return false;

    // Failure paths required by Phase 1A: wrong key, wrong nonce,
    // tampered ciphertext, and tampered tag all fall back to scatter.
    uint8_t bad_key[16];
    std::memcpy(bad_key, kTestKey, 16);
    bad_key[0] ^= 0x01;
    if (!is_scatter_failure(decrypt_config(bad_key, 16,
                                           kTestNonce, 12,
                                           kTestCiphertext, sizeof(kTestCiphertext),
                                           kTestTag, 16))) {
        return false;
    }

    uint8_t bad_nonce[12];
    std::memcpy(bad_nonce, kTestNonce, 12);
    bad_nonce[0] ^= 0x01;
    if (!is_scatter_failure(decrypt_config(kTestKey, 16,
                                           bad_nonce, 12,
                                           kTestCiphertext, sizeof(kTestCiphertext),
                                           kTestTag, 16))) {
        return false;
    }

    uint8_t bad_ciphertext[sizeof(kTestCiphertext)];
    std::memcpy(bad_ciphertext, kTestCiphertext, sizeof(kTestCiphertext));
    bad_ciphertext[0] ^= 0x01;
    if (!is_scatter_failure(decrypt_config(kTestKey, 16,
                                           kTestNonce, 12,
                                           bad_ciphertext, sizeof(bad_ciphertext),
                                           kTestTag, 16))) {
        return false;
    }

    if (!is_scatter_failure(tamper)) return false;

    return true;
}

std::string decrypt_config_test_registry() {
    const char* plaintext =
        "{\"schema_id\":\"test_r8071\",\"entries\":{\"conv.adapter_71\":{\"class\":\"kc5.v0\"}}}";
    std::vector<uint8_t> ciphertext;
    uint8_t tag[16] = {};
    if (!gcm_encrypt(kTestKey, kTestNonce, 12,
                     reinterpret_cast<const uint8_t*>(plaintext), std::strlen(plaintext),
                     ciphertext, tag)) {
        return kScatterRegistry;
    }
    auto result = decrypt_config(kTestKey, 16,
                                 kTestNonce, 12,
                                 ciphertext.data(), ciphertext.size(),
                                 tag, 16);
    return result.ok ? result.plaintext : std::string(kScatterRegistry);
}

}  // namespace guard
