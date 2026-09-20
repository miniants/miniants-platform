package cn.miniants.platform.security;

@FunctionalInterface
public interface EnforcementModeSource {

    EnforcementMode current();
}
