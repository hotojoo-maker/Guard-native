#include "../native_core/include/guard_core.h"
#include <cstdio>

int main() {
    bool ok = guard::decrypt_config_self_test();
    std::string reg = guard::decrypt_config_test_registry();
    printf("self_test=%s\n", ok ? "PASS" : "FAIL");
    printf("registry=%s\n", reg.c_str());
    return ok ? 0 : 1;
}
