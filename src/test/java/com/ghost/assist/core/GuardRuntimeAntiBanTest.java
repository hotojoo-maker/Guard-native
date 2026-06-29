package com.ghost.assist.core;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A2 anti-ban time-gate (D-020) pure-logic regression.
 *
 * Reflects the private static GuardRuntime#evalAntiBanWindow so production stays
 * byte-for-byte unchanged (no public test hatch). Lives in src/test/ — never
 * compiled into any APK variant, so reverse engineers see zero test trace.
 *
 * Gate semantics under test: install (true) unless a revoke window has elapsed.
 *   authorized            -> install
 *   revoked               -> cardRevokedAt + 72h, fail-open when no timestamp
 *   had token, expired    -> licenseExpire + 7d, fail-open when license cleared
 *   fresh, unauthorized   -> officialBase + 72h, fail-open when no clock
 */
public class GuardRuntimeAntiBanTest {

    private static final long H = 72L * 3600_000L;        // first-install / revoked grace
    private static final long D = 7L * 24L * 3600_000L;   // expired-license grace
    private static final long NOW = 1_800_000_000_000L;   // fixed ~2027 baseline, decoupled from wall clock

    private static boolean eval(boolean authorized, boolean cardRevoked, long cardRevokedAtMs,
                                boolean hasToken, long licenseExpireMs, long officialBaseMs, long now)
            throws Exception {
        Method m = GuardRuntime.class.getDeclaredMethod("evalAntiBanWindow",
                boolean.class, boolean.class, long.class,
                boolean.class, long.class, long.class, long.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, authorized, cardRevoked, cardRevokedAtMs,
                hasToken, licenseExpireMs, officialBaseMs, now);
    }

    // ── 1. authorized → always install ──
    @Test public void paid_alwaysInstalls() throws Exception {
        assertTrue(eval(true, false, 0L, true, 0L, 0L, NOW));
    }

    // ── 2. fresh + unauthorized → officialBase + 72h ──
    @Test public void fresh_in72h_installs() throws Exception {
        assertTrue(eval(false, false, 0L, false, 0L, NOW - H / 2, NOW));
    }

    @Test public void fresh_at72h_boundary_installs() throws Exception {
        assertTrue(eval(false, false, 0L, false, 0L, NOW - H, NOW));
    }

    @Test public void fresh_over72h_scatters() throws Exception {
        assertFalse(eval(false, false, 0L, false, 0L, NOW - H - 1, NOW));
    }

    @Test public void fresh_noClock_failOpen() throws Exception {
        assertTrue(eval(false, false, 0L, false, 0L, 0L, NOW));
    }

    // ── 3. had token, naturally expired → licenseExpire + 7d ──
    @Test public void expired_in7d_installs() throws Exception {
        assertTrue(eval(false, false, 0L, true, NOW - D / 2, 0L, NOW));
    }

    @Test public void expired_at7d_boundary_installs() throws Exception {
        assertTrue(eval(false, false, 0L, true, NOW - D, 0L, NOW));
    }

    @Test public void expired_over7d_scatters() throws Exception {
        assertFalse(eval(false, false, 0L, true, NOW - D - 1, 0L, NOW));
    }

    @Test public void expired_licenseCleared_failOpen() throws Exception {
        assertTrue(eval(false, false, 0L, true, 0L, 0L, NOW));
    }

    // ── 4. revoked (banned / card removed) → cardRevokedAt + 72h ──
    @Test public void revoked_in72h_installs() throws Exception {
        assertTrue(eval(false, true, NOW - H / 2, true, 0L, 0L, NOW));
    }

    @Test public void revoked_at72h_boundary_installs() throws Exception {
        assertTrue(eval(false, true, NOW - H, true, 0L, 0L, NOW));
    }

    @Test public void revoked_over72h_scatters() throws Exception {
        assertFalse(eval(false, true, NOW - H - 1, true, 0L, 0L, NOW));
    }

    @Test public void revoked_noTimestamp_failOpen() throws Exception {
        assertTrue(eval(false, true, 0L, true, 0L, 0L, NOW));
    }
}
