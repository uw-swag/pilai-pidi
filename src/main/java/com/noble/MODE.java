package com.noble;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

enum MODE {
    TESTING("testing"), NON_TESTING("non_testing");

    private final Boolean check_buffer;
    private final Boolean skip_srcml;
    private final Boolean skip_violations;
    private final List<String> lookup_string;

    MODE(String mode) {
        if (mode.equals("testing")) {
            this.skip_srcml = true;
            this.check_buffer = true;
            this.skip_violations = true;
            this.lookup_string = Arrays.asList("SkFlattenable", "SkReadBuffer");
        } else {
            this.skip_srcml = false;
            this.check_buffer = false;
            this.skip_violations = false;
            this.lookup_string = Collections.emptyList();
        }
    }

    public Boolean getCheck_buffer() {
        return check_buffer;
    }

    public Boolean getSkip_srcml() {
        return skip_srcml;
    }

    public Boolean getSkip_violations() {
        return skip_violations;
    }

    public List<String> getLookup_string() {
        return lookup_string;
    }
}
