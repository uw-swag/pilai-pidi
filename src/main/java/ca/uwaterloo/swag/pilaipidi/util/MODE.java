package ca.uwaterloo.swag.pilaipidi.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum MODE {
    TEST("test"), EXECUTE("execute");

    private final boolean checkBuffer;
    private final boolean startFromCpp;
    private final boolean exportGraph;
    private final boolean skipSrcml;
    private final boolean skipDataFlowAnalysis;
    private final List<String> lookupString;

    MODE(String mode) {
        if ("test".equals(mode)) {
            this.skipSrcml = true;
            this.startFromCpp = false;
            this.exportGraph = false;
            this.checkBuffer = false;
            this.skipDataFlowAnalysis = true;
            this.lookupString = Arrays.asList("shadePremulSpan");
        } else {
            this.skipSrcml = false;
            this.startFromCpp = false;
            this.exportGraph = false;
            this.checkBuffer = true;
            this.skipDataFlowAnalysis = false;
            this.lookupString = Collections.emptyList();
        }
    }

    public boolean checkBuffer() {
        return checkBuffer;
    }

    public boolean startFromCpp() {
        return startFromCpp;
    }

    public boolean exportGraph() {
        return exportGraph;
    }

    public boolean skipSrcml() {
        return skipSrcml;
    }

    public boolean skipDataFlowAnalysis() {
        return skipDataFlowAnalysis;
    }

    public List<String> lookupString() {
        return lookupString;
    }
}
