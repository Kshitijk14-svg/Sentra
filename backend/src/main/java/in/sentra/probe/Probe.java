package in.sentra.probe;

import in.sentra.check.Check;

public interface Probe {

    CheckType supports();

    ProbeResult execute(Check check);
}
