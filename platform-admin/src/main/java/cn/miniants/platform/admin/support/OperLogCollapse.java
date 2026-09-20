package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.entity.OperLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 按时间倒序后，连续相同用户 / IP / URI / 摘要折一行。
 */
public final class OperLogCollapse {

    private OperLogCollapse() {
    }

    public static List<OperLog> mergeConsecutive(List<OperLog> orderedDesc) {
        if (orderedDesc == null || orderedDesc.isEmpty()) {
            return List.of();
        }
        List<OperLog> collapsed = new ArrayList<>();
        OperLog current = null;
        int repeat = 0;
        for (OperLog row : orderedDesc) {
            if (current == null) {
                current = row;
                repeat = 1;
                continue;
            }
            if (sameGroup(current, row)) {
                repeat++;
                continue;
            }
            current.setRepeatCount(repeat);
            collapsed.add(current);
            current = row;
            repeat = 1;
        }
        current.setRepeatCount(repeat);
        collapsed.add(current);
        return collapsed;
    }

    static boolean sameGroup(OperLog left, OperLog right) {
        return Objects.equals(nvl(left.getOperatorName()), nvl(right.getOperatorName()))
                && Objects.equals(nvl(left.getRequestIp()), nvl(right.getRequestIp()))
                && Objects.equals(nvl(left.getRequestUri()), nvl(right.getRequestUri()))
                && Objects.equals(nvl(left.getErrorMessage()), nvl(right.getErrorMessage()));
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
