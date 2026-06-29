// registry_loader.cpp — Phase 1B: hook-config registry (plaintext prototype).
//
// Scope (auth-review PASS, 3 constraints):
//   1. Independent module — NOT mixed into config_crypto, NOT mixed into any
//      business filter, NOT wired into ConvFilter. This round only parses the
//      registry and surfaces it for verification ("did it hit?").
//   2. Parse failure → scatter registry (ok=false), never opens all, never
//      crashes. Built with -fno-exceptions / -fno-rtti, so the JSON parser is
//      hand-rolled and returns scatter on ANY malformed input.
//   3. Touches no AUTH_* / RISK_* constants; pure config-recipe parsing.
//
// Authority alignment (do not drift): conv.list anchors mirror
// moduleD/ConvFilter.java (L2-confirmed, P17 installed). 1C TODO: generate the
// embedded copy from native_core/registry_8071.json and later encrypt it via
// decrypt_config().

#include "guard_core.h"

#include <string>
#include <vector>
#include <utility>

namespace guard {
namespace {

// Phase 1C: registry is now an AES-GCM blob, not plaintext. The cipher bytes
// are GENERATED from registry_8071.json by tools/gen_registry_cipher.py (single
// source of truth — no hand-maintained plaintext copy). decrypt_config() turns
// it back into the registry JSON; tag mismatch / wrong key → scatter.
// Per-flavor 选择：coexist 变体（com.tencent.mn）嵌 8f47a47a 绑定的 registry，官替嵌
// e3e13a49 绑定的。CMake 按 GUARD_WX_PKG 定义 GUARD_REGISTRY_COEXIST；两 inc 符号名
// 相同（kRegistry*），必须二选一（docs/RELEASE_LINE_SSOT_发行线统一口径.md）。
#if defined(GUARD_REGISTRY_COEXIST)
#include "registry_cipher_coexist.inc"
#else
#include "registry_cipher.inc"
#endif

// C2: cert-only bootstrap endpoint blob (AUTH server domains). Generated from
// native_core/bootstrap_endpoints.json by tools/gen_bootstrap_cipher.py.
// Decrypted with derive_bootstrap_key() (no server seed) so endpoints are
// available before any server handshake. See docs/HONEYPOT_蜜罐设计.md §4.
// Per-flavor 选择（同上 registry）：bootstrap 也绑 cert，coexist 变体（com.tencent.mn）
// 嵌 8f47a47a 绑定的引导段，官替嵌 e3e13a49 绑定的。漏了它 → coexist 解不开服务器地址
// → bootstrapEndpoints count=0 → 连不上服务器。两 inc 符号名相同（kBootstrap*），二选一。
#if defined(GUARD_REGISTRY_COEXIST)
#include "bootstrap_cipher_coexist.inc"
#else
#include "bootstrap_cipher.inc"
#endif

#ifndef GUARD_REGISTRY_REQUIRES_SERVER_SEED
#define GUARD_REGISTRY_REQUIRES_SERVER_SEED 0
#endif

// ── Minimal JSON parser (objects + strings only) ──────────────
// Registry is shallow: { string|object }. Numbers/arrays/bool/null are not
// part of the schema; encountering them is treated as malformed → scatter.

struct JValue {
    bool is_string = false;
    bool is_object = false;
    std::string str;                                       // when is_string
    std::vector<std::pair<std::string, JValue>> members;   // when is_object
};

size_t skip_ws(const std::string& s, size_t i) {
    while (i < s.size()) {
        const char c = s[i];
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') ++i;
        else break;
    }
    return i;
}

// Parse a JSON string token at s[i]=='"'. Fills out. Returns index past the
// closing quote, or npos on error.
size_t parse_string(const std::string& s, size_t i, std::string& out) {
    if (i >= s.size() || s[i] != '"') return std::string::npos;
    ++i;
    std::string r;
    while (i < s.size()) {
        const char c = s[i];
        if (c == '"') { out = r; return i + 1; }
        if (c == '\\') {
            if (i + 1 >= s.size()) return std::string::npos;
            const char n = s[i + 1];
            switch (n) {
                case '"':  r.push_back('"');  break;
                case '\\': r.push_back('\\'); break;
                case '/':  r.push_back('/');  break;
                case 'n':  r.push_back('\n'); break;
                case 't':  r.push_back('\t'); break;
                case 'r':  r.push_back('\r'); break;
                default: return std::string::npos;  // unsupported escape
            }
            i += 2;
        } else {
            r.push_back(c);
            ++i;
        }
    }
    return std::string::npos;  // unterminated string
}

size_t parse_value(const std::string& s, size_t i, JValue& out);  // fwd

size_t parse_object(const std::string& s, size_t i, JValue& out) {
    if (i >= s.size() || s[i] != '{') return std::string::npos;
    ++i;
    out.is_object = true;
    i = skip_ws(s, i);
    if (i < s.size() && s[i] == '}') return i + 1;  // empty object
    while (i < s.size()) {
        i = skip_ws(s, i);
        std::string key;
        i = parse_string(s, i, key);
        if (i == std::string::npos) return std::string::npos;
        i = skip_ws(s, i);
        if (i >= s.size() || s[i] != ':') return std::string::npos;
        ++i;
        JValue child;
        i = parse_value(s, i, child);
        if (i == std::string::npos) return std::string::npos;
        out.members.emplace_back(key, std::move(child));
        i = skip_ws(s, i);
        if (i >= s.size()) return std::string::npos;
        if (s[i] == ',') { ++i; continue; }
        if (s[i] == '}') return i + 1;
        return std::string::npos;  // unexpected token
    }
    return std::string::npos;
}

size_t parse_value(const std::string& s, size_t i, JValue& out) {
    i = skip_ws(s, i);
    if (i >= s.size()) return std::string::npos;
    const char c = s[i];
    if (c == '"') {
        out.is_string = true;
        return parse_string(s, i, out.str);
    }
    if (c == '{') {
        return parse_object(s, i, out);
    }
    return std::string::npos;  // unsupported value type → malformed
}

const JValue* find_member(const JValue& obj, const std::string& key) {
    if (!obj.is_object) return nullptr;
    for (const auto& kv : obj.members) {
        if (kv.first == key) return &kv.second;
    }
    return nullptr;
}

ConfigRegistry make_scatter() {
    ConfigRegistry r;
    r.ok = false;
    r.schema_id = "scatter";
    r.wechat_version = "0.0.0";
    return r;
}

}  // namespace

ConfigRegistry registry_parse(const std::string& json) {
    JValue root;
    size_t idx = parse_value(json, 0, root);
    if (idx == std::string::npos) return make_scatter();
    idx = skip_ws(json, idx);
    if (idx != json.size()) return make_scatter();  // trailing garbage
    if (!root.is_object) return make_scatter();

    const JValue* schema = find_member(root, "schema_id");
    if (schema == nullptr || !schema->is_string) return make_scatter();

    const JValue* entries = find_member(root, "entries");
    if (entries == nullptr || !entries->is_object) return make_scatter();

    ConfigRegistry out;
    out.ok = true;
    out.schema_id = schema->str;
    const JValue* ver = find_member(root, "wechat_version");
    out.wechat_version = (ver != nullptr && ver->is_string) ? ver->str : std::string();

    for (const auto& entry_kv : entries->members) {
        const JValue& entry_obj = entry_kv.second;
        if (!entry_obj.is_object) return make_scatter();  // entry must be object
        RegistryEntry re;
        re.id = entry_kv.first;
        for (const auto& field_kv : entry_obj.members) {
            if (!field_kv.second.is_string) return make_scatter();  // fields are strings
            re.fields.emplace_back(field_kv.first, field_kv.second.str);
        }
        out.entries.emplace_back(std::move(re));
    }
    return out;
}

ConfigRegistry registry_load_embedded() {
    // Phase 1D-local: key is derived in-SO (no plaintext key constant), nonce is
    // random per build. Decrypt the embedded AES-GCM registry blob, then parse.
    // Any tag/key/nonce mismatch → scatter (decrypt_config returns ok=false).
    // Phase 1D-server S3a: prod_server_lock generated blobs require a valid
    // server seed from the envelope before decrypt. DEV cert-only blobs preserve
    // current P_NC1 behavior and skip this guard.
    if (GUARD_REGISTRY_REQUIRES_SERVER_SEED && !server_seed_ready()) {
        return make_scatter();
    }
    uint8_t key[16];
    derive_registry_key(key);
    ConfigDecryptResult dec = decrypt_config(
        key, sizeof(key),
        kRegistryNonce, sizeof(kRegistryNonce),
        kRegistryCipher, sizeof(kRegistryCipher),
        kRegistryTag, sizeof(kRegistryTag));
    if (!dec.ok) return make_scatter();
    return registry_parse(dec.plaintext);
}

bool registry_requires_server_seed() {
    // True for prod_server_lock cipher blobs (generated with a server seed). When
    // true and no seed is loaded, registry_load_embedded() intentionally scatters
    // — that is the lock working, NOT a drift failure. Lets the build-time gate
    // (test_config_crypto) skip registry_self_test cleanly without a real S_rel.
    return GUARD_REGISTRY_REQUIRES_SERVER_SEED != 0;
}

namespace {
const std::string* find_field(const RegistryEntry& e, const std::string& key) {
    for (const auto& kv : e.fields) {
        if (kv.first == key) return &kv.second;
    }
    return nullptr;
}

const RegistryEntry* find_entry(const ConfigRegistry& r, const std::string& id) {
    for (const auto& e : r.entries) {
        if (e.id == id) return &e;
    }
    return nullptr;
}

// Verify entry exists and field[key] == expected.
bool field_eq(const ConfigRegistry& r, const std::string& id,
              const std::string& key, const std::string& expected) {
    const RegistryEntry* e = find_entry(r, id);
    if (e == nullptr) return false;
    const std::string* v = find_field(*e, key);
    return v != nullptr && *v == expected;
}
}  // namespace

// Phase 1E Step1: single recipe lookup for the SO→Java channel.
// Decrypts the embedded registry, returns entries[gateway].fields[key].
// fail-closed: scatter (decrypt fail) / unknown gateway / unknown field → "".
// Pure read; does NOT take over any Filter (ConvFilter etc. untouched).
std::string registry_get_recipe(const std::string& gateway,
                                const std::string& key) {
    ConfigRegistry r = registry_load_embedded();
    if (!r.ok) return std::string();
    const RegistryEntry* e = find_entry(r, gateway);
    if (e == nullptr) return std::string();
    const std::string* v = find_field(*e, key);
    if (v == nullptr) return std::string();
    return *v;
}

namespace {
// C2: decrypt the cert-only bootstrap blob into a registry struct. Reuses the
// same shallow JSON parser as the main registry; key is derive_bootstrap_key()
// (no server seed). Any tag/key mismatch → scatter (hard fail-closed).
ConfigRegistry bootstrap_load_embedded() {
    uint8_t key[16];
    derive_bootstrap_key(key);
    ConfigDecryptResult dec = decrypt_config(
        key, sizeof(key),
        kBootstrapNonce, sizeof(kBootstrapNonce),
        kBootstrapCipher, sizeof(kBootstrapCipher),
        kBootstrapTag, sizeof(kBootstrapTag));
    if (!dec.ok) return make_scatter();
    return registry_parse(dec.plaintext);
}
}  // namespace

std::string bootstrap_get_endpoint(const std::string& key) {
    ConfigRegistry r = bootstrap_load_embedded();
    if (!r.ok) return std::string();
    // AUTH servers first (net.endpoint: primary/backup1/...). Keeping this lookup
    // first preserves the exact behaviour AppConfig.guardServerList() relies on.
    const RegistryEntry* e = find_entry(r, "net.endpoint");
    if (e != nullptr) {
        const std::string* v = find_field(*e, key);
        if (v != nullptr) return *v;
    }
    // role-B / other entries (e.g. cs.endpoint -> funnel landing): match by field
    // key across the remaining entries. Field keys are unique so this never
    // pollutes the auth list (guardServerList only ever asks for primary/backup*).
    for (const auto& entry : r.entries) {
        const std::string* v = find_field(entry, key);
        if (v != nullptr) return *v;
    }
    return std::string();
}

std::string registry_dump_summary() {
    ConfigRegistry r = registry_load_embedded();
    if (!r.ok) return "scatter";
    std::string s = "schema=" + r.schema_id + " ver=" + r.wechat_version +
                    " entries=" + std::to_string(r.entries.size());
    for (const auto& e : r.entries) {
        s += " [" + e.id;
        const std::string* adapter = find_field(e, "adapter_class");
        if (adapter != nullptr) s += " adapter=" + *adapter;
        s += "]";
    }
    return s;
}

bool registry_self_test() {
    // (1) Embedded registry parses and matches the Filter-layer anchors
    //     (conv/moments/contact/search + a2.sig — L2-confirmed in moduleD/moduleB
    //      + A2SignatureSpoof).
    ConfigRegistry r = registry_load_embedded();
    if (!r.ok) return false;
    if (r.schema_id != "r8071_v1") return false;
    if (r.wechat_version != "8.0.71") return false;
    if (r.entries.size() != 5) return false;  // conv/moments/contact/search + a2.sig (A2-1)

    // conv.list (ConvFilter.java)
    if (!field_eq(r, "conv.list", "mvvmlist_class",
                  "com.tencent.mm.plugin.mvvmlist.MvvmList")) return false;
    if (!field_eq(r, "conv.list", "adapter_class", "kc5.v0")) return false;
    {
        const RegistryEntry* e = find_entry(r, "conv.list");
        const std::string* getters = (e != nullptr) ? find_field(*e, "wxid_getters") : nullptr;
        if (getters == nullptr || getters->find("C0") == std::string::npos) return false;
    }

    // moments.feed (MomentsFilter.java)
    if (!field_eq(r, "moments.feed", "item_friend", "na4.b")) return false;
    if (!field_eq(r, "moments.feed", "adapter_class", "e2")) return false;
    {
        const RegistryEntry* e = find_entry(r, "moments.feed");
        const std::string* actors = (e != nullptr) ? find_field(*e, "actor_field_names") : nullptr;
        if (actors == nullptr || actors->find("f435583d") == std::string::npos) return false;
    }

    // contact.address (ContactFilter.java)
    if (!field_eq(r, "contact.address", "item_class", "fc5.g")) return false;
    if (!field_eq(r, "contact.address", "contact_class", "com.tencent.mm.storage.z3")) return false;
    if (!field_eq(r, "contact.address", "wxid_getter", "c1")) return false;

    // search.gateway — coarse-grained capability entry (search is a huge filter
    // covering friends/recent/groups/chat-history; we express a render gateway +
    // extractor profile, NOT one capability per result class). The 111111 unlock
    // (EntryGate) is intentionally out of scope for this gateway.
    if (!field_eq(r, "search.gateway", "gateway", "fts_result_view")) return false;
    if (!field_eq(r, "search.gateway", "render_hook", "getView")) return false;
    if (!field_eq(r, "search.gateway", "extractor_profile", "wechat8071_fts_mixed")) return false;
    {
        const RegistryEntry* e = find_entry(r, "search.gateway");
        const std::string* fam = (e != nullptr) ? find_field(*e, "adapter_family") : nullptr;
        if (fam == nullptr) return false;
        if (fam->find("q2") == std::string::npos) return false;
        if (fam->find("f0") == std::string::npos) return false;
    }

    // a2.sig (A2SignatureSpoof anti-ban official DER; entry #5, added with A2-1)
    {
        const RegistryEntry* e = find_entry(r, "a2.sig");
        const std::string* der = (e != nullptr) ? find_field(*e, "official_der") : nullptr;
        if (der == nullptr) return false;
        if (der->rfind("308202eb", 0) != 0) return false;  // official cert DER header (751B hex)
    }

    // (1c) Encrypted-path integrity: a tampered cipher byte must fail the GCM
    //      tag (no real entries leak).
    {
        uint8_t key[16];
        derive_registry_key(key);
        std::vector<uint8_t> bad(kRegistryCipher, kRegistryCipher + sizeof(kRegistryCipher));
        if (!bad.empty()) bad[0] ^= 0x01;
        ConfigDecryptResult dec = decrypt_config(
            key, sizeof(key),
            kRegistryNonce, sizeof(kRegistryNonce),
            bad.data(), bad.size(),
            kRegistryTag, sizeof(kRegistryTag));
        if (dec.ok) return false;                          // must fail tag
        if (registry_parse(dec.plaintext).entries.size() != 0) return false;  // no leak
    }

    // (1d) Wrong derived key must scatter (proves the key derivation is
    //      required — a flipped key bit fails the GCM tag, no entries leak).
    {
        uint8_t key[16];
        derive_registry_key(key);
        key[0] ^= 0x01;
        ConfigDecryptResult dec = decrypt_config(
            key, sizeof(key),
            kRegistryNonce, sizeof(kRegistryNonce),
            kRegistryCipher, sizeof(kRegistryCipher),
            kRegistryTag, sizeof(kRegistryTag));
        if (dec.ok) return false;
    }

    // (1e) Phase 1E Step1: recipe getter returns the right field, and misses
    //      fail-closed to empty string (unknown gateway / unknown field).
    if (registry_get_recipe("conv.list", "adapter_class") != "kc5.v0") return false;
    if (registry_get_recipe("search.gateway", "gateway") != "fts_result_view") return false;
    if (!registry_get_recipe("no.such.gateway", "adapter_class").empty()) return false;
    if (!registry_get_recipe("conv.list", "no_such_field").empty()) return false;

    // (2) Malformed inputs must scatter (not throw, not partial-open).
    if (registry_parse("").ok) return false;
    if (registry_parse("{").ok) return false;
    if (registry_parse("{\"schema_id\":\"x\"}").ok) return false;        // no entries
    if (registry_parse("{\"entries\":{}}").ok) return false;            // no schema_id
    if (registry_parse("{\"schema_id\":\"x\",\"entries\":{}} junk").ok) return false;  // trailing
    if (registry_parse("{\"schema_id\":\"x\",\"entries\":{\"a\":\"b\"}}").ok) return false;  // entry not object

    // (3) A well-formed minimal registry parses ok.
    ConfigRegistry mini = registry_parse(
        "{\"schema_id\":\"mini\",\"entries\":{\"e1\":{\"k\":\"v\"}}}");
    if (!mini.ok) return false;
    if (mini.schema_id != "mini" || mini.entries.size() != 1) return false;

    return true;
}

}  // namespace guard
