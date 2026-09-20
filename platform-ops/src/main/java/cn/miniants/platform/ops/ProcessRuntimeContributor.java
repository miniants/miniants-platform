package cn.miniants.platform.ops;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProcessRuntimeContributor implements RuntimeStateContributor {

    private final RuntimeDrain drain;
    private final Instant startedAt = Instant.now();

    public ProcessRuntimeContributor(RuntimeDrain drain) {
        this.drain = drain;
    }

    @Override
    public String getName() {
        return "process";
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("draining", drain.isDraining());
        state.put("startedAt", startedAt.toString());
        return state;
    }

    @Override
    public boolean supportsDrain() {
        return true;
    }

    @Override
    public void setDraining(boolean draining) {
        drain.setDraining(draining);
    }
}
