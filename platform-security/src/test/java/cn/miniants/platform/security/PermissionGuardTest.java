package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PermissionGuardTest {

    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void adminAlwaysAllows() {
        assertEquals(PermissionGuard.Outcome.ALLOW,
                guard.decide("sys:user:page", true, Set.of(), EnforcementMode.ENFORCE));
    }

    @Test
    void orCodeMatchesFirstGranted() {
        assertEquals("kiosk-tch", PermissionGuard.firstMatch(Set.of("kiosk-tch"), "kiosk-stu|kiosk-tch"));
        assertNull(PermissionGuard.firstMatch(Set.of("other"), "kiosk-stu|kiosk-tch"));
    }

    @Test
    void mismatchIsShadowOrEnforce() {
        assertEquals(PermissionGuard.Outcome.SHADOW_DENY,
                guard.decide("sys:user:page", false, Set.of(), EnforcementMode.SHADOW));
        assertEquals(PermissionGuard.Outcome.DENY,
                guard.decide("sys:user:page", false, Set.of(), EnforcementMode.ENFORCE));
        assertEquals(PermissionGuard.Outcome.ALLOW,
                guard.decide("sys:user:page", false, Set.of(), EnforcementMode.OFF));
    }

    @Test
    void ignoreSkipsCodeCheck() {
        assertEquals(PermissionGuard.Outcome.ALLOW,
                guard.decide(true, "sys:user:page", false, Set.of(), EnforcementMode.ENFORCE));
    }
}
