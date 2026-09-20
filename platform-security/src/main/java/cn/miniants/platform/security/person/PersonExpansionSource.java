package cn.miniants.platform.security.person;

import java.util.List;

/**
 * 已知自然人时返回还应有的账号提示。无 Bean = 不扩账号。
 */
public interface PersonExpansionSource {

    /**
     * 仅凭已持久化的 person 扩号。无证号场景可走此方法。
     */
    List<PrincipalHint> expand(Person person);

    /**
     * 验真入参在内存：明文证号只用于本次花名册查询，不得落库或打日志。
     * 缺省委托 {@link #expand(Person)}。
     */
    default List<PrincipalHint> expand(Person person, String idDocType, String rawIdNo) {
        return expand(person);
    }
}
