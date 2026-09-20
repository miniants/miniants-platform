package cn.miniants.platform.ops;

import java.util.concurrent.atomic.AtomicBoolean;

public class LocalRuntimeDrain implements RuntimeDrain {

    private final AtomicBoolean draining = new AtomicBoolean();

    @Override
    public boolean isDraining() {
        return draining.get();
    }

    @Override
    public void setDraining(boolean draining) {
        this.draining.set(draining);
    }
}
