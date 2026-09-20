package cn.miniants.platform.ratelimit.client;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析客户端地址：默认只信直连 peer；仅当 peer 落在可信代理 CIDR 内时才解析
 * {@code Forwarded} / {@code X-Forwarded-For}，取最右侧非可信地址，防止头部伪造。
 */
public final class ClientAddressResolver {

    private static final Pattern FORWARDED_FOR = Pattern.compile(
            "for=([^;\\s,]+)", Pattern.CASE_INSENSITIVE);

    private final List<Cidr> trustedProxies;

    public ClientAddressResolver(List<String> trustedProxyCidrs) {
        this.trustedProxies = parseCidrs(trustedProxyCidrs);
    }

    public static ClientAddressResolver of(List<String> trustedProxyCidrs) {
        return new ClientAddressResolver(trustedProxyCidrs);
    }

    /**
     * @param remoteAddress 直连 peer（已去方括号的 IPv6 亦可）
     * @param forwarded     {@code Forwarded} 头（可多值拼接或逗号分隔）
     * @param xForwardedFor {@code X-Forwarded-For} 头
     * @return 客户端 IP 字符串；无法解析时回落 remoteAddress 或 {@code unknown}
     */
    public String resolve(String remoteAddress, String forwarded, String xForwardedFor) {
        String peer = normalizeIp(remoteAddress);
        if (peer == null || peer.isBlank()) {
            peer = "unknown";
        }
        if (trustedProxies.isEmpty() || !isTrusted(peer)) {
            return peer;
        }

        List<String> chain = new ArrayList<>();
        List<String> fromForwarded = parseForwarded(forwarded);
        if (!fromForwarded.isEmpty()) {
            chain.addAll(fromForwarded);
        } else {
            chain.addAll(parseXff(xForwardedFor));
        }
        if (chain.isEmpty()) {
            return peer;
        }

        // 从右往左跳过可信代理，第一个非可信即为客户端
        for (int i = chain.size() - 1; i >= 0; i--) {
            String hop = chain.get(i);
            if (hop == null || hop.isBlank() || "unknown".equalsIgnoreCase(hop)) {
                continue;
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return peer;
    }

    public boolean isTrusted(String ip) {
        String normalized = normalizeIp(ip);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        byte[] address;
        try {
            address = InetAddress.getByName(normalized).getAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
        for (Cidr cidr : trustedProxies) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> parseXff(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        String[] parts = header.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            String ip = normalizeIp(part);
            if (ip != null && !ip.isBlank()) {
                out.add(ip);
            }
        }
        return out;
    }

    private static List<String> parseForwarded(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        Matcher matcher = FORWARDED_FOR.matcher(header);
        List<String> out = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw == null) {
                continue;
            }
            String ip = normalizeIp(stripQuotes(raw));
            if (ip != null && !ip.isBlank()) {
                out.add(ip);
            }
        }
        return out;
    }

    private static String stripQuotes(String value) {
        String v = value.trim();
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    /**
     * 去掉端口、方括号、可选的 {@code unknown} 包装。
     */
    static String normalizeIp(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.regionMatches(true, 0, "for=", 0, 4)) {
            value = value.substring(4).trim();
            value = stripQuotes(value);
        }
        // [IPv6]:port 或 [IPv6]
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 1) {
                return value.substring(1, end);
            }
        }
        // IPv4:port（仅一个冒号且看起来像端口）
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            String host = value.substring(0, colon);
            String port = value.substring(colon + 1);
            if (host.indexOf('.') >= 0 && port.chars().allMatch(Character::isDigit)) {
                return host;
            }
        }
        return value;
    }

    private static List<Cidr> parseCidrs(List<String> cidrs) {
        if (cidrs == null || cidrs.isEmpty()) {
            return List.of();
        }
        List<Cidr> out = new ArrayList<>(cidrs.size());
        for (String raw : cidrs) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            out.add(Cidr.parse(raw.trim()));
        }
        return Collections.unmodifiableList(out);
    }

    static final class Cidr {

        private final byte[] network;
        private final int prefixLength;

        private Cidr(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static Cidr parse(String text) {
            Objects.requireNonNull(text, "text");
            String trimmed = text.trim();
            String hostPart;
            int prefix;
            int slash = trimmed.indexOf('/');
            if (slash < 0) {
                hostPart = trimmed;
                try {
                    byte[] addr = InetAddress.getByName(normalizeIp(hostPart)).getAddress();
                    prefix = addr.length * 8;
                    return new Cidr(addr, prefix);
                } catch (UnknownHostException ex) {
                    throw new IllegalArgumentException("无法解析可信代理地址: " + text, ex);
                }
            }
            hostPart = trimmed.substring(0, slash).trim();
            try {
                prefix = Integer.parseInt(trimmed.substring(slash + 1).trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("无法解析可信代理 CIDR: " + text, ex);
            }
            try {
                byte[] addr = InetAddress.getByName(normalizeIp(hostPart)).getAddress();
                int max = addr.length * 8;
                if (prefix < 0 || prefix > max) {
                    throw new IllegalArgumentException("CIDR 前缀长度非法: " + text);
                }
                return new Cidr(mask(addr, prefix), prefix);
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException("无法解析可信代理 CIDR: " + text, ex);
            }
        }

        boolean contains(byte[] address) {
            if (address == null || address.length != network.length) {
                return false;
            }
            if (prefixLength == 0) {
                return true;
            }
            BigInteger net = new BigInteger(1, network);
            BigInteger ip = new BigInteger(1, mask(address, prefixLength));
            return net.equals(ip);
        }

        private static byte[] mask(byte[] address, int prefixLength) {
            byte[] copy = address.clone();
            int fullBytes = prefixLength / 8;
            int remBits = prefixLength % 8;
            for (int i = fullBytes + (remBits > 0 ? 1 : 0); i < copy.length; i++) {
                copy[i] = 0;
            }
            if (remBits > 0 && fullBytes < copy.length) {
                int mask = 0xFF << (8 - remBits);
                copy[fullBytes] = (byte) (copy[fullBytes] & mask);
            }
            return copy;
        }
    }
}
