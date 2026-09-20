package cn.miniants.platform.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumUtil {

    private NumUtil() {
    }

    public static Double round(Double d) {
        return round(d, 2);
    }

    public static Double round(Double d, int len) {
        double value = d == null ? 0.0d : d;
        return BigDecimal.valueOf(value).setScale(len, RoundingMode.HALF_UP).doubleValue();
    }
}
