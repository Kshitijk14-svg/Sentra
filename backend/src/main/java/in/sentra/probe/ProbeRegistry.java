package in.sentra.probe;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProbeRegistry {

    private final Map<CheckType, Probe> probes = new EnumMap<>(CheckType.class);

    public ProbeRegistry(List<Probe> all) {
        for (Probe probe : all) {
            probes.put(probe.supports(), probe);
        }
    }

    public Probe get(CheckType type) {
        Probe probe = probes.get(type);
        if (probe == null) {
            throw new IllegalStateException("No probe registered for check type " + type);
        }
        return probe;
    }
}
