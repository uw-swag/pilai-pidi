package ca.uwaterloo.swag.pilaipidi.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public
enum MODE {
    TEST("test"), EXECUTE("execute");

    private final Boolean checkBuffer;
    private final Boolean startFromCpp;
    private final Boolean exportGraph;
    private final Boolean skipSrcml;
    private final Boolean skipViolations;
    private final List<String> lookupString;

    MODE(String mode) {
        if ("test".equals(mode)) {
            this.skipSrcml = true;
            this.startFromCpp = false;
            this.exportGraph = false;
            this.checkBuffer = false;
            this.skipViolations = true;
            this.lookupString = Arrays.asList("shadePremulSpan");
        } else {
            this.skipSrcml = false;
            this.startFromCpp = false;
            this.exportGraph = false;
            this.checkBuffer = true;
            this.skipViolations = false;
            this.lookupString = Collections.emptyList();
        }
    }

    public Boolean checkBuffer() {
        return checkBuffer;
    }

    public Boolean startFromCpp() {
        return startFromCpp;
    }

    public Boolean exportGraph() {
        return exportGraph;
    }

    public Boolean skipSrcml() {
        return skipSrcml;
    }

    public Boolean skipViolations() {
        return skipViolations;
    }

    public List<String> lookupString() {
        return lookupString;
    }
}
