package cn.miniants.platform.data.audit;

import java.util.Optional;

public class EmptyAuditorSupplier implements AuditorSupplier {

    @Override
    public Optional<Auditor> current() {
        return Optional.empty();
    }
}
