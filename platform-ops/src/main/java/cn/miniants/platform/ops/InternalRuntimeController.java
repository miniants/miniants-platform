package cn.miniants.platform.ops;

import cn.miniants.platform.core.api.RawBody;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RawBody
@RestController
@RequestMapping(InternalRuntimeAccessFilter.INTERNAL_RUNTIME_PATH)
public class InternalRuntimeController {

    private final ObjectProvider<RuntimeStateContributor> contributors;

    public InternalRuntimeController(ObjectProvider<RuntimeStateContributor> contributors) {
        this.contributors = contributors;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        contributors.orderedStream().forEach(contributor ->
                state.put(contributor.getName(), contributor.getState()));
        return state;
    }

    @PutMapping("/drain")
    public Map<String, Object> drain(@RequestParam(defaultValue = "true") boolean enabled) {
        contributors.orderedStream()
                .filter(RuntimeStateContributor::supportsDrain)
                .forEach(contributor -> contributor.setDraining(enabled));
        return state();
    }
}
