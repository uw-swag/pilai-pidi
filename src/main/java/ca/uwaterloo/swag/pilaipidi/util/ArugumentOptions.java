package ca.uwaterloo.swag.pilaipidi.util;

import java.util.List;
import java.util.Map;

public class ArugumentOptions {

    public final List<String> argsList;
    public final Map<String, String> optsList;
    public final List<String> doubleOptsList;
    public final String projectLocation;
    public final String[] singleTarget;

    public ArugumentOptions(List<String> argsList, Map<String, String> optsList,
                            List<String> doubleOptsList, String projectLocation, String[] singleTarget) {
        this.argsList = argsList;
        this.optsList = optsList;
        this.doubleOptsList = doubleOptsList;
        this.projectLocation = projectLocation;
        this.singleTarget = singleTarget;
    }
}
