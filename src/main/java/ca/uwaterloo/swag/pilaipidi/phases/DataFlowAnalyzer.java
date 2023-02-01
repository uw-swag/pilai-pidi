package ca.uwaterloo.swag.pilaipidi.phases;

import ca.uwaterloo.swag.pilaipidi.models.ArgumentNamePos;
import ca.uwaterloo.swag.pilaipidi.models.CFunction;
import ca.uwaterloo.swag.pilaipidi.models.DFGNode;
import ca.uwaterloo.swag.pilaipidi.models.DFGNodeCFunction;
import ca.uwaterloo.swag.pilaipidi.models.DataAccess;
import ca.uwaterloo.swag.pilaipidi.models.DataAccess.DataAccessType;
import ca.uwaterloo.swag.pilaipidi.models.FunctionNamePos;
import ca.uwaterloo.swag.pilaipidi.models.NamePos;
import ca.uwaterloo.swag.pilaipidi.models.SliceProfile;
import ca.uwaterloo.swag.pilaipidi.models.SliceProfilesInfo;
import ca.uwaterloo.swag.pilaipidi.models.SliceVariableAccess;
import ca.uwaterloo.swag.pilaipidi.models.Value;
import ca.uwaterloo.swag.pilaipidi.util.MODE;
import ca.uwaterloo.swag.pilaipidi.util.TypeChecker;
import ca.uwaterloo.swag.pilaipidi.util.XmlUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.w3c.dom.Node;

/**
 * {@link DataFlowAnalyzer} is responsible for analysing the slice profiles to detect possible sinks.
 *
 * @since 0.0.1
 */
public class DataFlowAnalyzer {

    private final String JNI_NATIVE_METHOD_MODIFIER = "native";
    private final Set<SliceProfile> analyzedProfiles = new HashSet<>();
    private final Map<String, SliceProfilesInfo> javaSliceProfilesInfo = new Hashtable<>();
    private final Map<String, SliceProfilesInfo> cppSliceProfilesInfo = new Hashtable<>();
    private final Map<String, SliceProfilesInfo> sliceProfilesInfo;
    private final Graph<DFGNode, DefaultEdge> graph;
    private final Map<DFGNode, List<String>> dataFlowPaths;
    private final List<String> sinkFunctions;
    private final String[] singleTarget;
    private final MODE mode;

    public DataFlowAnalyzer(Map<String, SliceProfilesInfo> sliceProfilesInfo, Graph<DFGNode, DefaultEdge> graph,
                            Map<DFGNode, List<String>> dataFlowPaths, List<String> sinkFunctions,
                            String[] singleTarget, MODE mode) {
        this.sliceProfilesInfo = sliceProfilesInfo;
        this.graph = graph;
        this.dataFlowPaths = dataFlowPaths;
        this.sinkFunctions = sinkFunctions;
        this.singleTarget = singleTarget;
        this.mode = mode;
    }

    public void analyze() {
        for (String sliceKey : sliceProfilesInfo.keySet()) {
            if (sliceKey.endsWith(".java")) {
                javaSliceProfilesInfo.put(sliceKey, sliceProfilesInfo.get(sliceKey));
            } else {
                cppSliceProfilesInfo.put(sliceKey, sliceProfilesInfo.get(sliceKey));
            }
        }

        if (mode.startFromCpp()) {
            analyzeCppProfiles(cppSliceProfilesInfo);
        } else {
            analyzeJavaProfiles(javaSliceProfilesInfo);
        }

//        printDFGNodes(graph);
    }

    private void analyzeCppProfiles(Map<String, SliceProfilesInfo> cppSliceProfilesInfo) {
        for (SliceProfilesInfo currentSlice : cppSliceProfilesInfo.values()) {
            for (SliceProfile profile : currentSlice.sliceProfiles.values()) {
                if (isAnalyzedProfile(profile)) {
                    continue;
                }
                analyzeSliceProfile(profile, cppSliceProfilesInfo);
            }
        }
    }

    private void analyzeJavaProfiles(Map<String, SliceProfilesInfo> javaSliceProfilesInfo) {
        for (SliceProfilesInfo currentSlice : javaSliceProfilesInfo.values()) {
            for (SliceProfile profile : currentSlice.sliceProfiles.values()) {
                if (isAnalyzedProfile(profile)) {
                    continue;
                }
                analyzeSliceProfile(profile, javaSliceProfilesInfo);
            }
        }
    }

    private boolean isAnalyzedProfile(SliceProfile profile) {
        return analyzedProfiles.contains(profile);
    }


    private void analyzeSliceProfile(SliceProfile profile, Map<String, SliceProfilesInfo> rawProfilesInfo) {
        analyzedProfiles.add(profile);

        // step-01 : analyse cfunctions of the slice variable
        DFGNode dfgNode;
        for (CFunction cFunction : profile.cfunctions) {
            analyzeCFunction(cFunction, profile, rawProfilesInfo);
        }

        dfgNode = new DFGNode(profile.varName, profile.functionName, profile.fileName, profile.definedPosition,
                profile.isFunctionNameProfile, profile.typeName);
        if (!graph.containsVertex(dfgNode)) {
            graph.addVertex(dfgNode);
        }

        // step-02 : analyze data dependent vars of the slice variable
        for (NamePos dependentVar : profile.dependentVars) {
            String dvarName = dependentVar.getName();
            String dvarEnclFunctionName = dependentVar.getType();
            String dvarPos = dependentVar.getPos();
            Map<String, SliceProfile> sourceSliceProfiles = rawProfilesInfo.get(profile.fileName).sliceProfiles;
            String sliceKey = dvarName + "%" + dvarPos + "%" + dvarEnclFunctionName + "%" + profile.fileName;
            if (!sourceSliceProfiles.containsKey(sliceKey)) {
                // not capturing struct/class var assignments
                continue;
            }
            SliceProfile dvarSliceProfile = sourceSliceProfiles.get(sliceKey);
            checkAndUpdateDependentProfileValue(dvarSliceProfile, profile);
            DFGNode dVarDFGNode = new DFGNode(dvarSliceProfile.varName,
                    dvarSliceProfile.functionName, dvarSliceProfile.fileName,
                    dvarSliceProfile.definedPosition, dvarSliceProfile.isFunctionNameProfile,
                dvarSliceProfile.typeName);
            if (!hasNoEdge(dfgNode, dVarDFGNode)) {
                continue;
            }
            if (isAnalyzedProfile(dvarSliceProfile)) {
                continue;
            }
            analyzeSliceProfile(dvarSliceProfile, rawProfilesInfo);
            checkForBufferAssignment(dvarSliceProfile, profile, dfgNode, dvarPos);
        }

        // step-03 : analyze if given function node is a native method
        if (!profile.functionName.equals("GLOBAL") && profile.cfunctions.size() < 1
                && profile.functionNode != null) {
            Node enclFunctionNode = profile.functionNode;
            if (XmlUtil.isFunctionOfGivenModifier(enclFunctionNode, JNI_NATIVE_METHOD_MODIFIER)) {
                analyzeNativeFunction(profile, rawProfilesInfo, enclFunctionNode, dfgNode);
            }
        }

        if (!mode.checkBuffer()) {
            return;
        }

        if (profile.fileName.endsWith(".java")) {
            return;
        }

        if (singleTarget != null) {
            return;
        }

        // step-04 : check buffer reads and writes for this profile
        analyzeBufferAccess(profile, dfgNode);

        // step-05 : check pointer reads and writes for this profile
//        analyzePointerAccess(profile, rawProfilesInfo, dfgNode);
    }

    private void checkForBufferAssignment(SliceProfile lhsProfile, SliceProfile rhsProfile, DFGNode dfgNode,
                                          String dvarPos) {
        if (rhsProfile.fileName.endsWith(".java")) {
            return;
        }

        if (!lhsProfile.isBuffer || lhsProfile.getAssignedProfile() != rhsProfile) {
            return;
        }

        if (isWithinConditionalBound(lhsProfile)) {
            return;
        } else if (isAccessWithinBufferBound(lhsProfile.getCurrentValue().getBufferSize(),
            rhsProfile.getCurrentValue())) {
            return;
        }
        List<String> dataFlowIssues = new ArrayList<>();
        if (dataFlowPaths.containsKey(dfgNode)) {
            dataFlowIssues = new ArrayList<>(dataFlowPaths.get(dfgNode));
        }
        dataFlowIssues.add("Buffer overflow assing at " + dfgNode.fileName() + "," + dvarPos);
        dataFlowPaths.put(dfgNode, dataFlowIssues);
    }

    private void printDFGNodes(Graph<DFGNode, DefaultEdge> graph) {
        for (DFGNode dfgNode : graph.vertexSet()) {
            System.out.println(dfgNode);
        }
    }

    private void analyzeCFunction(CFunction cFunction, SliceProfile profile,
                                  Map<String, SliceProfilesInfo> sliceProfilesInfo) {
        if (singleTarget != null) {
            return;
        }

        String cfunctionName = cFunction.getName();
        String cfunctionPos = cFunction.getPosition();
        String enclFunctionName = cFunction.getEnclFunctionName();
        DFGNode dfgNode = new DFGNodeCFunction(profile.varName, enclFunctionName, profile.fileName,
                profile.definedPosition, profile.typeName, cFunction.getIsLocalCall(),
                cFunction.getNumberOfArguments());

        if (sinkFunctions.contains(cfunctionName)) {
            if (isBufferAccessFunction(cfunctionName) && isBufferAccessFunctionWithinBound(cFunction)) {
                return;
            }
            graph.addVertex(dfgNode);
            ArrayList<String> cErrors = new ArrayList<>();
            cErrors.add("Use of " + cfunctionName + " at " + dfgNode.fileName() + "," + cfunctionPos);
            DFGNode bufferErrorFunctionDFGNode = new DFGNode(dfgNode.varName() + "#" + cfunctionName,
                    dfgNode.functionName(), dfgNode.fileName(), cfunctionPos, true, dfgNode.varType());
            hasNoEdge(dfgNode, bufferErrorFunctionDFGNode);
            dataFlowPaths.put(bufferErrorFunctionDFGNode, cErrors);
            return;
        }

        LinkedList<SliceProfile> dependentSliceProfiles = findDependentSliceProfiles(cFunction, profile.typeName,
                sliceProfilesInfo);
        for (SliceProfile dependentSliceProfile : dependentSliceProfiles) {
            checkAndUpdateDependentProfileValue(dependentSliceProfile, profile);
            DFGNode depNameDFGNode = new DFGNodeCFunction(dependentSliceProfile.varName,
                    dependentSliceProfile.functionName, dependentSliceProfile.fileName,
                    dependentSliceProfile.definedPosition, dependentSliceProfile.isFunctionNameProfile,
                    dependentSliceProfile.typeName, cFunction.getIsLocalCall(), cFunction.getNumberOfArguments());
            if (!hasNoEdge(dfgNode, depNameDFGNode)) {
                continue;
            }
            if (isAnalyzedProfile(dependentSliceProfile)) {
                continue;
            }
            analyzeSliceProfile(dependentSliceProfile, sliceProfilesInfo);
        }
    }

    private void checkAndUpdateDependentProfileValue(SliceProfile rhsProfile, SliceProfile lhsProfile) {
        if (rhsProfile.isFunctionNameProfile || lhsProfile.isFunctionNameProfile) {
            return;
        }

        if (rhsProfile.isBuffer && rhsProfile.getCurrentValue() != null &&
            !rhsProfile.getCurrentValue().isReferenced()) { // already has a literal value
            return;
        }

        if (rhsProfile.getCurrentValue() != null && !rhsProfile.getCurrentValue().isReferenced()) {
            rhsProfile.setCurrentValue(new Value(rhsProfile.getCurrentValue().literal, lhsProfile));
        } else {
            rhsProfile.setCurrentValue(new Value(lhsProfile));
        }
    }

    private LinkedList<SliceProfile> findDependentSliceProfiles(CFunction cFunction, String typeName,
                                                                Map<String, SliceProfilesInfo> profilesInfoMap) {
        LinkedList<SliceProfile> dependentSliceProfiles = new LinkedList<>();
        for (String filePath : profilesInfoMap.keySet()) {
            SliceProfilesInfo profileInfo = profilesInfoMap.get(filePath);
            List<FunctionNamePos> possibleFunctions = findPossibleFunctions(profileInfo.functionNodes,
                    profileInfo.functionDeclMap, cFunction, typeName);
            for (FunctionNamePos functionNamePos : possibleFunctions) {
                if (cFunction.isEmptyArgFunc() || functionNamePos.getArguments() == null ||
                        functionNamePos.getArguments().isEmpty() ||
                        cFunction.getArgPosIndex() >= functionNamePos.getArguments().size()) {
                    continue;
                }

                // 01 - Add function arg index based profile
                NamePos param = functionNamePos.getArguments().get(cFunction.getArgPosIndex());
                String param_name = param.getName();
                String param_pos = param.getPos();
                String key = param_name + "%" + param_pos + "%" + functionNamePos.getName() + "%" + filePath;
                if (!profileInfo.sliceProfiles.containsKey(key)) {
                    continue;
                }
                dependentSliceProfiles.add(profileInfo.sliceProfiles.get(key));

                // 02 - Add cfunction profile
                key = functionNamePos.getName() + "%" + functionNamePos.getPos() + "%" +
                    functionNamePos.getName() + "%" + filePath;
                if (!profileInfo.sliceProfiles.containsKey(key)) {
                    continue;
                }
                dependentSliceProfiles.add(profileInfo.sliceProfiles.get(key));
            }
        }
        return dependentSliceProfiles;
    }


    private void analyzeNativeFunction(SliceProfile profile, Map<String, SliceProfilesInfo> profilesInfo,
                                       Node enclFunctionNode, DFGNode dfgNode) {
        Node enclUnitNode = profilesInfo.get(profile.fileName).unitNode;
        String jniFunctionName = getJNIFunctionNameString(profile);
        String jniArgName = profile.varName;
        List<ArgumentNamePos> parameters = XmlUtil.findFunctionParameters(enclFunctionNode);
        int index = 0;
        for (NamePos parameter : parameters) {
            if (parameter.getName().equals(jniArgName)) {
                break;
            }
            index++;
        }
        int jniArgPosIndex = index + 2; // first two arugments in native jni methods are env and obj
        String clazzName = XmlUtil.getJavaClassName(enclUnitNode);
        String jniFunctionSearchStr = clazzName + "_" + jniFunctionName;

        for (String filePath : cppSliceProfilesInfo.keySet()) {
            SliceProfilesInfo profileInfo = cppSliceProfilesInfo.get(filePath);

            for (FunctionNamePos funcNamePos : profileInfo.functionNodes.keySet()) {
                Node functionNode = profileInfo.functionNodes.get(funcNamePos);
                String functionName = funcNamePos.getName();

                if (!functionName.toLowerCase().contains(clazzName.toLowerCase())) {
                    continue;
                }

                String cleanedFunctionName = cleanUpJNIFunctionName(functionName);
                if (!cleanedFunctionName.toLowerCase().endsWith(jniFunctionSearchStr.toLowerCase())) {
                    continue;
                }

                // 01 - Function arg index based profile
                List<ArgumentNamePos> functionArgs = XmlUtil.findFunctionParameters(functionNode);
                if (functionArgs.size() < 1 || jniArgPosIndex > functionArgs.size() - 1) {
                    continue;
                }

                NamePos arg = functionArgs.get(jniArgPosIndex);
                String sliceKey = arg.getName() + "%" + arg.getPos() + "%" + functionName + "%" + filePath;
                analyzeNativeSliceProfile(dfgNode, profileInfo, sliceKey, profile);

                // 02 - Native cfunction profile
                sliceKey = functionName + "%" + funcNamePos.getPos() + "%" + functionName + "%" + filePath;
                analyzeNativeSliceProfile(dfgNode, profileInfo, sliceKey, profile);
            }
        }
    }

    private void checkAndUpdateNativeArrayValue(SliceProfile lhsProfile, SliceProfile rhsProifle) {
        if (rhsProifle.cfunctions.stream().anyMatch(c -> c.getName().equals("GetPrimitiveArrayCritical"))) {
            rhsProifle.setCurrentValue(new Value(lhsProfile));
        }
    }

    private String cleanUpJNIFunctionName(String functionName) {
        return functionName.replaceAll("_[0-9]", "_");
    }

    private String getJNIFunctionNameString(SliceProfile profile) {
        String jniFunctionName = profile.functionName;
        if (jniFunctionName.length() > 2 && jniFunctionName.startsWith("n") &&
                Character.isUpperCase(jniFunctionName.charAt(1))) {
            jniFunctionName = jniFunctionName.substring(1);
        }
        return jniFunctionName;
    }

    private void analyzeNativeSliceProfile(DFGNode dfgNode, SliceProfilesInfo profileInfo, String sliceKey,
                                                   SliceProfile parentProfile) {
        if (!profileInfo.sliceProfiles.containsKey(sliceKey)) {
            return;
        }
        SliceProfile sliceProfile = profileInfo.sliceProfiles.get(sliceKey);
        checkAndUpdateDependentProfileValue(sliceProfile, parentProfile);
        DFGNode analyzedNameDFGNode = new DFGNode(sliceProfile.varName, sliceProfile.functionName,
                sliceProfile.fileName, sliceProfile.definedPosition, sliceProfile.isFunctionNameProfile,
            sliceProfile.typeName);
        if (!hasNoEdge(dfgNode, analyzedNameDFGNode)) {
            return;
        }
        if (isAnalyzedProfile(sliceProfile)) {
            return;
        }
        analyzeSliceProfile(sliceProfile, cppSliceProfilesInfo);
    }

    private boolean hasNoEdge(DFGNode sourceNameNode, DFGNode targetNameNode) {
        if (sourceNameNode.equals(targetNameNode)) {
            return false;
        }
        if (!graph.containsVertex(sourceNameNode)) {
            graph.addVertex(sourceNameNode);
        }
        if (!graph.containsVertex(targetNameNode)) {
            graph.addVertex(targetNameNode);
        }
        if (graph.containsEdge(sourceNameNode, targetNameNode)) {
            return false;
        }

        graph.addEdge(sourceNameNode, targetNameNode);
        return true;
    }

    private List<FunctionNamePos> findPossibleFunctions(Map<FunctionNamePos, Node> functionNodes,
                                                        Map<String, List<FunctionNamePos>> functionDeclMap,
                                                        CFunction cFunction, String argTypeName) {
        String cfunctionName = cFunction.getName();
        int argPosIndex = cFunction.getArgPosIndex();
        Node enclFunctionNode = cFunction.getEnclFunctionNode();
        List<FunctionNamePos> possibleFunctions = new ArrayList<>();

        if (enclFunctionNode == null) {
            return possibleFunctions;
        }

        List<String> cFunctionsWithAlias = new ArrayList<>();
        cFunctionsWithAlias.add(cfunctionName);

        if (functionDeclMap.containsKey(cfunctionName)) {
            cFunctionsWithAlias.addAll(functionDeclMap.get(cfunctionName)
                    .stream()
                    .map(FunctionNamePos::getFunctionDeclName)
                    .collect(Collectors.toList()));
        }

        for (String cFuncName : cFunctionsWithAlias) {
            for (FunctionNamePos functionNamePos : functionNodes.keySet()) {
                String functionName = functionNamePos.getName();
                if (!functionName.equals(cFuncName)) {
                    continue;
                }
                if (cFunction.isEmptyArgFunc()) {
                    possibleFunctions.add(functionNamePos);
                    continue;
                }
                if (!typeCheckFunctionSignature(functionNamePos, argPosIndex, argTypeName, cFunction)) {
                    continue;
                }
                possibleFunctions.add(functionNamePos);
            }
        }
        return possibleFunctions;
    }

    private boolean typeCheckFunctionSignature(FunctionNamePos functionNamePos, int argPosIndex, String argTypeName,
                                               CFunction cFunction) {
        List<ArgumentNamePos> funcArgs = functionNamePos.getArguments();
        if (funcArgs.size() == 0 || argPosIndex >= funcArgs.size()) {
            return false;
        }
        ArgumentNamePos namePos = funcArgs.get(argPosIndex);
        if (namePos == null) {
            return false;
        }
        String paramName = namePos.getName();
        if (paramName.equals("")) {
            return false;
        }

        if (cFunction.getNumberOfArguments() < getRequiredArgumentsSize(funcArgs)) {
            return false;
        }

        return TypeChecker.isAssignable(namePos.getType(), argTypeName);
    }

    private int getRequiredArgumentsSize(List<ArgumentNamePos> funcArgs) {
        return (int) funcArgs.stream().filter(arg -> !arg.isOptional()).count();
    }

    private void analyzeBufferAccess(SliceProfile profile, DFGNode dfgNode) {
        for (SliceVariableAccess varAccess : profile.usedPositions) {
            for (DataAccess access : varAccess.writePositions) {
                if (DataAccessType.BUFFER_WRITE == access.accessType) {
                    if (isAccessWithinBufferBound(profile.getCurrentValue(), access.accessedExprValue)) {
                        continue;
                    }
                    List<String> dataFlowIssues = new ArrayList<>();
                    if (dataFlowPaths.containsKey(dfgNode)) {
                        dataFlowIssues = new ArrayList<>(dataFlowPaths.get(dfgNode));
                    }
                    dataFlowIssues.add("Buffer write at " + dfgNode.fileName() + "," +
                            access.accessedExprNamePos.getPos());
                    dataFlowPaths.put(dfgNode, dataFlowIssues);
                }
            }
            for (DataAccess access : varAccess.readPositions) {
                if (DataAccessType.BUFFER_READ == access.accessType) {
                    if (isAccessWithinBufferBound(profile.getCurrentValue(), access.accessedExprValue)) {
                        continue;
                    }
                    List<String> dataFlowIssues = new ArrayList<>();
                    if (dataFlowPaths.containsKey(dfgNode)) {
                        dataFlowIssues = new ArrayList<>(dataFlowPaths.get(dfgNode));
                    }
                    dataFlowIssues.add("Buffer read at " + dfgNode.fileName() + "," +
                            access.accessedExprNamePos.getPos());
                    dataFlowPaths.put(dfgNode, dataFlowIssues);
                }
            }
        }
    }

    private boolean isAccessWithinBufferBound(Value bufferSizeValue, Value bufferAccessValue) {
        int bufferSize = getValue(bufferSizeValue, new HashSet<>());
        int bufferAccessedSize = getValue(bufferAccessValue, new HashSet<>());
//        if (bufferSize == 0 && bufferAccessedSize == 0) { // we did not capture the sizes properly
//            return false;
//        }
        return bufferSize > bufferAccessedSize;
    }

    private boolean isAccessWithinOrEqualToBufferBound(Value bufferSizeValue, Value bufferAccessValue) {
        int bufferSize = getValue(bufferSizeValue, new HashSet<>());
        int bufferAccessedSize = getValue(bufferAccessValue, new HashSet<>());
//        if (bufferSize == 0 && bufferAccessedSize == 0) { // we did not capture the sizes properly
//            return false;
//        }
        return bufferSize >= bufferAccessedSize;
    }

    private int getValue(Value value, Set<SliceProfile> checkedProfiles) {
        if (value == null) {
            return 0;
        }
        if (value.isReferenced()) {
            SliceProfile referencedProfile = value.referencedProfile;
            if (checkedProfiles.contains(referencedProfile)) {
                return value.literal;
            }
            checkedProfiles.add(referencedProfile);
            return getValue(referencedProfile.getCurrentValue(), checkedProfiles);
        }
        return value.literal;
    }

    private boolean isBufferAccessFunctionWithinBound(CFunction cFunction) {
        switch (cFunction.getName()) {
            case "strcpy":
                return checkStrCpy(cFunction);
            case "strcat":
                return checkStrCat(cFunction);
            case "strncat":
                return checkStrnCat(cFunction);
            case "strncmp":
                return checkStrnCmp(cFunction);
            case "memcpy":
                return checkMemCpy(cFunction);
            case "memccpy":
                return checkMemCCpy(cFunction);
            case "memmove":
                return checkMemMove(cFunction);
            case "memcmp":
                return checkMemCmp(cFunction);
            case "memset":
                return checkMemSet(cFunction);
            case "bcopy":
                return checkBCopy(cFunction);
            case "bzero":
                return checkBZero(cFunction);
            case "strdup":
            case "strcmp":
            case "strncpy":
            case "strlen":
            case "strchr":
            case "strrchr":
            case "index":
            case "rindex":
            case "strpbrk":
            case "strspn":
            case "strcspn":
            case "strstr":
            case "strtok":
            case "memchr":
            case "bcmp":
                break;
        }
        return false;
    }

    private boolean isBufferAccessFunction(String cfunctionName) {
        return sinkFunctions.contains(cfunctionName);
    }

    private boolean checkMemCpy(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 3) { // TODO, size has to be 3
            return false;
        }
        SliceProfile dst = argProfiles.get(0);
        SliceProfile src = argProfiles.get(1);
        SliceProfile bound = argProfiles.get(2);

        if (isWithinConditionalBound(src) || isWithinConditionalBound(dst)) {
            return true;
        }

        return isAccessWithinBufferBound(dst.getCurrentValue(), bound.getCurrentValue()) &&
                isAccessWithinBufferBound(src.getCurrentValue(), bound.getCurrentValue());
    }

    private boolean checkMemCCpy(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 4) { // TODO, size has to be 3
            return false;
        }
        SliceProfile dst = argProfiles.get(0);
        SliceProfile src = argProfiles.get(1);
        SliceProfile bound = argProfiles.get(3);
        return isAccessWithinBufferBound(dst.getCurrentValue(), bound.getCurrentValue()) &&
                isAccessWithinBufferBound(src.getCurrentValue(), bound.getCurrentValue());
    }

    private boolean checkMemMove(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 3) { // TODO, size has to be 3
            return false;
        }
        SliceProfile dst = argProfiles.get(0);
        SliceProfile src = argProfiles.get(1);
        SliceProfile bound = argProfiles.get(2);
        return isAccessWithinBufferBound(dst.getCurrentValue(), bound.getCurrentValue()) &&
                isAccessWithinBufferBound(src.getCurrentValue(), bound.getCurrentValue());
    }

    private boolean checkMemCmp(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 3) { // TODO, size has to be 3
            return false;
        }
        SliceProfile str1 = argProfiles.get(0);
        SliceProfile str2 = argProfiles.get(1);
        SliceProfile bound = argProfiles.get(2);
        return isAccessWithinBufferBound(str1.getCurrentValue(), bound.getCurrentValue())
                && isAccessWithinBufferBound(str2.getCurrentValue(), bound.getCurrentValue());
    }

    private boolean checkMemSet(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 3) { // TODO, size has to be 3
            return false;
        }
        SliceProfile dst = argProfiles.get(0);
        SliceProfile bound = argProfiles.get(2);
        return isAccessWithinBufferBound(dst.getCurrentValue(), bound.getCurrentValue());
    }

    private boolean checkStrCpy(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 2) { // TODO, size has to be 2
            return false;
        }

        SliceProfile src = argProfiles.get(0);
        SliceProfile dst = argProfiles.get(1);
        if (isWithinConditionalBound(src) || isWithinConditionalBound(dst)) {
            return true;
        }
        return isAccessWithinOrEqualToBufferBound(src.getCurrentValue(), dst.getCurrentValue());
    }

    private boolean isWithinConditionalBound(SliceProfile src) {
        return src.getEnclosingConditionProfile() != null && src.getCurrentValue() != null &&
            isAccessWithinBufferBound(src.getCurrentValue().getBufferSize(),
                src.getEnclosingConditionProfile().getCurrentValue());
    }

    private boolean checkStrCat(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 2) { // TODO, size has to be 2
            return false;
        }

        SliceProfile src = argProfiles.get(0);
        SliceProfile dst = argProfiles.get(1);
        return isAccessWithinOrEqualToBufferBound(src.getCurrentValue(), dst.getCurrentValue());
    }

    private boolean checkStrnCat(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 3) { // TODO, size has to be 3
            return false;
        }
        SliceProfile dst = argProfiles.get(0);
        SliceProfile bound = argProfiles.get(2);
        return isAccessWithinBufferBound(dst.getCurrentValue(), bound.getCurrentValue());
    }

    private boolean checkStrnCmp(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 3) { // TODO, size has to be 3
            return false;
        }
        SliceProfile str1 = argProfiles.get(0);
        SliceProfile str2 = argProfiles.get(1);
        SliceProfile bound = argProfiles.get(2);
        return isAccessWithinBufferBound(str1.getCurrentValue(), bound.getCurrentValue())
                && isAccessWithinBufferBound(str2.getCurrentValue(), bound.getCurrentValue());
    }

    private boolean checkBCopy(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 3) { // TODO, size has to be 3
            return false;
        }
        SliceProfile dst = argProfiles.get(0);
        SliceProfile src = argProfiles.get(1);
        SliceProfile bound = argProfiles.get(2);
        return isAccessWithinBufferBound(dst.getCurrentValue(), bound.getCurrentValue()) &&
                isAccessWithinBufferBound(src.getCurrentValue(), bound.getCurrentValue());
    }

    private boolean checkBZero(CFunction cFunction) {
        List<SliceProfile> argProfiles = cFunction.getArgProfiles();
        if (argProfiles.size() != 2) { // TODO, size has to be 2
            return false;
        }

        SliceProfile src = argProfiles.get(0);
        SliceProfile dst = argProfiles.get(1);
        return isAccessWithinOrEqualToBufferBound(src.getCurrentValue(), dst.getCurrentValue());
    }

    private void analyzePointerAccess(SliceProfile profile, Map<String, SliceProfilesInfo> rawProfilesInfo,
                                      DFGNode dfgNode) {
        for (SliceVariableAccess varAccess : profile.dataAccess) {
            for (DataAccess access : varAccess.writePositions) {
                NamePos dependentVar = access.accessedExprNamePos;
                String dvarName = dependentVar.getName();
                String dvarEnclFunctionName = dependentVar.getType();
                String dvarPos = dependentVar.getPos();
                Map<String, SliceProfile> sourceSliceProfiles = rawProfilesInfo.get(profile.fileName).sliceProfiles;
                String sliceKey = dvarName + "%" + dvarPos + "%" + dvarEnclFunctionName + "%" + profile.fileName;
                if (!sourceSliceProfiles.containsKey(sliceKey)) { // not capturing struct/class var assignments
                    continue;
                }
                SliceProfile dvarSliceProfile = sourceSliceProfiles.get(sliceKey);
                DFGNode dVarNameDFGNode = new DFGNode(dvarSliceProfile.varName,
                        dvarSliceProfile.functionName, dvarSliceProfile.fileName,
                        dvarSliceProfile.definedPosition, dvarSliceProfile.isFunctionNameProfile,
                    dvarSliceProfile.typeName);
                if (hasNoEdge(dfgNode, dVarNameDFGNode) && !isAnalyzedProfile(dvarSliceProfile)) {
                    analyzeSliceProfile(dvarSliceProfile, rawProfilesInfo);
                }

                if (dvarSliceProfile.isPointer && DataAccessType.DATA_WRITE == access.accessType) {
                    List<String> dataFlowIssues;
                    if (dataFlowPaths.containsKey(dVarNameDFGNode)) {
                        dataFlowIssues = new ArrayList<>(dataFlowPaths.get(dVarNameDFGNode));
                    } else {
                        dataFlowIssues = new ArrayList<>();
                    }
                    dataFlowIssues.add("Pointer data write of '" + dvarSliceProfile.varName + "' at " +
                            access.accessedExprNamePos.getPos());
                    dataFlowPaths.put(dVarNameDFGNode, dataFlowIssues);
                }
            }
        }
    }

}
