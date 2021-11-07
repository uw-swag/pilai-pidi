package ca.uwaterloo.swag.pilaipidi.phases;

import ca.uwaterloo.swag.pilaipidi.util.MODE;
import ca.uwaterloo.swag.pilaipidi.models.ArgumentNamePos;
import ca.uwaterloo.swag.pilaipidi.models.CFunction;
import ca.uwaterloo.swag.pilaipidi.models.DataTuple;
import ca.uwaterloo.swag.pilaipidi.models.EnclNamePosTuple;
import ca.uwaterloo.swag.pilaipidi.models.FunctionNamePos;
import ca.uwaterloo.swag.pilaipidi.models.NamePos;
import ca.uwaterloo.swag.pilaipidi.models.SliceProfile;
import ca.uwaterloo.swag.pilaipidi.models.SliceProfilesInfo;
import ca.uwaterloo.swag.pilaipidi.models.SliceVariableAccess;
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

public class DataFlowAnalyzer {

    private final String JNI_NATIVE_METHOD_MODIFIER = "native";
    private final Set<SliceProfile> analyzedProfiles = new HashSet<>();
    private final Map<String, SliceProfilesInfo> javaSliceProfilesInfo = new Hashtable<>();
    private final Map<String, SliceProfilesInfo> cppSliceProfilesInfo = new Hashtable<>();
    private final Map<String, SliceProfilesInfo> sliceProfilesInfo;
    private final Graph<EnclNamePosTuple, DefaultEdge> graph;
    private final Map<EnclNamePosTuple, List<String>> detectedViolations;
    private final List<String> sinkFunctions;
    private final String[] singleTarget;
    private final MODE mode;

    public DataFlowAnalyzer(Map<String, SliceProfilesInfo> sliceProfilesInfo,
                            Graph<EnclNamePosTuple, DefaultEdge> graph,
                            Map<EnclNamePosTuple, List<String>> detectedViolations, List<String> sinkFunctions,
                            String[] singleTarget, MODE mode) {
        this.sliceProfilesInfo = sliceProfilesInfo;
        this.graph = graph;
        this.detectedViolations = detectedViolations;
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
//                log.log(Level.INFO, "Source Prof -> " + profile.varName + "%" + profile.functionName +
//                    "%" + profile.fileName + "%" + profile.definedPosition);
                analyzeSliceProfile(profile, javaSliceProfilesInfo);
            }
        }
    }

    private boolean isAnalyzedProfile(SliceProfile profile) {
//        log.log(Level.INFO, "Profile '" + profile.toString() + "'" + " analyzed : " + contains);
        return analyzedProfiles.contains(profile);
    }


    private void analyzeSliceProfile(SliceProfile profile, Map<String, SliceProfilesInfo> rawProfilesInfo) {
        analyzedProfiles.add(profile);

        // step-01 : analyse cfunctions of the slice variable
        EnclNamePosTuple enclNamePosTuple;
        for (CFunction cFunction : profile.cfunctions) {
            analyzeCfunction(cFunction, profile, rawProfilesInfo);
        }
        enclNamePosTuple = new EnclNamePosTuple(profile.varName, profile.functionName, profile.fileName,
            profile.definedPosition);
        if (!graph.containsVertex(enclNamePosTuple)) {
            graph.addVertex(enclNamePosTuple);
        }

        // step-02 : analyze data dependent vars of the slice variable
        for (NamePos dependentVar : profile.dependentVars) {
            String dvarName = dependentVar.getName();
            String dvarEnclFunctionName = dependentVar.getType();
            String dvarPos = dependentVar.getPos();
            Map<String, SliceProfile> sourceSliceProfiles = rawProfilesInfo.get(profile.fileName).sliceProfiles;
            String sliceKey =
                dvarName + "%" + dvarPos + "%" + dvarEnclFunctionName + "%" + profile.fileName;
            if (!sourceSliceProfiles.containsKey(sliceKey)) {
                // not capturing struct/class var assignments
                continue;
            }
            SliceProfile dvarSliceProfile = sourceSliceProfiles.get(sliceKey);
            EnclNamePosTuple dvarNamePosTuple = new EnclNamePosTuple(dvarSliceProfile.varName,
                dvarSliceProfile.functionName, dvarSliceProfile.fileName,
                dvarSliceProfile.definedPosition);
            if (!hasNoEdge(enclNamePosTuple, dvarNamePosTuple)) {
                continue;
            }
            if (isAnalyzedProfile(dvarSliceProfile)) {
                continue;
            }
            analyzeSliceProfile(dvarSliceProfile, rawProfilesInfo);
        }

        // step-03 : analyze if given function node is a native method
        if (!profile.functionName.equals("GLOBAL") && profile.cfunctions.size() < 1
            && profile.functionNode != null) {
            Node enclFunctionNode = profile.functionNode;
            if (isFunctionOfGivenModifier(enclFunctionNode, JNI_NATIVE_METHOD_MODIFIER)) {
                analyzeNativeFunction(profile, rawProfilesInfo, enclFunctionNode, enclNamePosTuple);
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

        // step-04 : check and add buffer reads and writes for this profile
        for (SliceVariableAccess varAccess : profile.usedPositions) {
            for (DataTuple access : varAccess.writePositions) {
                if (XmlUtil.DataAccessType.BUFFER_WRITE == access.accessType) {
                    List<String> violations;
                    if (detectedViolations.containsKey(enclNamePosTuple)) {
                        violations = new ArrayList<>(detectedViolations.get(enclNamePosTuple));
                    } else {
                        violations = new ArrayList<>();
                    }
                    violations.add("Buffer write at " + access.accessVarNamePos.getPos());
                    detectedViolations.put(enclNamePosTuple, violations);
                } else if (XmlUtil.DataAccessType.BUFFER_READ == access.accessType) {
                    List<String> violations;
                    if (detectedViolations.containsKey(enclNamePosTuple)) {
                        violations = new ArrayList<>(detectedViolations.get(enclNamePosTuple));
                    } else {
                        violations = new ArrayList<>();
                    }
                    violations.add("Buffer read at " + access.accessVarNamePos.getPos());
                    detectedViolations.put(enclNamePosTuple, violations);
                }
            }
        }

        for (SliceVariableAccess varAccess : profile.dataAccess) {
            for (DataTuple access : varAccess.writePositions) {
                NamePos dependentVar = access.accessVarNamePos;
                String dvarName = dependentVar.getName();
                String dvarEnclFunctionName = dependentVar.getType();
                String dvarPos = dependentVar.getPos();
                Map<String, SliceProfile> sourceSliceProfiles = rawProfilesInfo.get(profile.fileName).sliceProfiles;
                String sliceKey = dvarName + "%" + dvarPos + "%" + dvarEnclFunctionName + "%" + profile.fileName;
                if (!sourceSliceProfiles.containsKey(sliceKey)) { // not capturing struct/class var assignments
                    continue;
                }
                SliceProfile dvarSliceProfile = sourceSliceProfiles.get(sliceKey);
                EnclNamePosTuple dvarNamePosTuple = new EnclNamePosTuple(dvarSliceProfile.varName,
                    dvarSliceProfile.functionName, dvarSliceProfile.fileName,
                    dvarSliceProfile.definedPosition);
                if (hasNoEdge(enclNamePosTuple, dvarNamePosTuple) && !isAnalyzedProfile(dvarSliceProfile)) {
                    analyzeSliceProfile(dvarSliceProfile, rawProfilesInfo);
                }

                if (dvarSliceProfile.isPointer && XmlUtil.DataAccessType.DATA_WRITE == access.accessType) {
                    ArrayList<String> violations;
                    if (detectedViolations.containsKey(dvarNamePosTuple)) {
                        violations = new ArrayList<>(detectedViolations.get(dvarNamePosTuple));
                    } else {
                        violations = new ArrayList<>();
                    }
                    violations.add("Pointer data write of '" + dvarSliceProfile.varName + "' at " +
                        access.accessVarNamePos.getPos());
                    detectedViolations.put(dvarNamePosTuple, violations);
                }
            }
        }
    }

    private void analyzeCfunction(CFunction cFunction, SliceProfile profile,
                                  Map<String, SliceProfilesInfo> sliceProfilesInfo) {
        if (singleTarget != null) {
            return;
        }

        String cfunctionName = cFunction.getName();
        String cfunctionPos = cFunction.getPosition();
        String enclFunctionName = cFunction.getEnclFunctionName();
        EnclNamePosTuple enclNamePosTuple = new EnclNamePosTuple(profile.varName, enclFunctionName, profile.fileName,
            profile.definedPosition);

        if (sinkFunctions.contains(cfunctionName)) {
            graph.addVertex(enclNamePosTuple);
            ArrayList<String> cErrors = new ArrayList<>();
            cErrors.add("Use of " + cfunctionName + " at " + cfunctionPos);
            EnclNamePosTuple bufferErrorFunctionPosTuple =
                new EnclNamePosTuple(enclNamePosTuple.varName() + "#" + cfunctionName,
                    enclNamePosTuple.functionName(), enclNamePosTuple.fileName(), cfunctionPos, true);
            hasNoEdge(enclNamePosTuple, bufferErrorFunctionPosTuple);
            detectedViolations.put(bufferErrorFunctionPosTuple, cErrors);
            return;
        }

        LinkedList<SliceProfile> dependentSliceProfiles = findDependentSliceProfiles(cFunction, profile.typeName,
            sliceProfilesInfo);
        for (SliceProfile dependentSliceProfile : dependentSliceProfiles) {
            EnclNamePosTuple depNamePosTuple = new EnclNamePosTuple(dependentSliceProfile.varName,
                dependentSliceProfile.functionName, dependentSliceProfile.fileName,
                dependentSliceProfile.definedPosition, dependentSliceProfile.isFunctionNameProfile);
            if (!hasNoEdge(enclNamePosTuple, depNamePosTuple)) {
                continue;
            }
            if (isAnalyzedProfile(dependentSliceProfile)) {
                continue;
            }
            analyzeSliceProfile(dependentSliceProfile, sliceProfilesInfo);
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
                // 01 - Add cfunction profile
                String key = functionNamePos.getName() + "%" + functionNamePos.getPos() + "%" +
                    functionNamePos.getName() + "%" + filePath;
                if (!profileInfo.sliceProfiles.containsKey(key)) {
                    continue;
                }
                dependentSliceProfiles.add(profileInfo.sliceProfiles.get(key));

                if (cFunction.isEmptyArgFunc() || functionNamePos.getArguments() == null ||
                    functionNamePos.getArguments().isEmpty()) {
                    continue;
                }

                // 02 - Add function arg index based profile
                NamePos param = functionNamePos.getArguments().get(cFunction.getArgPosIndex());
                String param_name = param.getName();
                String param_pos = param.getPos();
                key = param_name + "%" + param_pos + "%" + functionNamePos.getName() + "%" + filePath;
                if (!profileInfo.sliceProfiles.containsKey(key)) {
                    continue;
                }
                dependentSliceProfiles.add(profileInfo.sliceProfiles.get(key));
            }
        }
        return dependentSliceProfiles;
    }


    private void analyzeNativeFunction(SliceProfile profile, Map<String, SliceProfilesInfo> profilesInfo,
                                              Node enclFunctionNode, EnclNamePosTuple enclNamePosTuple) {
        Node enclUnitNode = profilesInfo.get(profile.fileName).unitNode;
        String jniFunctionName = profile.functionName;
        if (jniFunctionName.length() > 2 && jniFunctionName.startsWith("n") &&
            Character.isUpperCase(jniFunctionName.charAt(1))) {
            jniFunctionName = jniFunctionName.substring(1);
        }
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
        String clazzName = XmlUtil.getNodeByName(XmlUtil.getNodeByName(enclUnitNode, "class").get(0),
            "name").get(0).getTextContent();
        String jniFunctionSearchStr = clazzName + "_" + jniFunctionName;

        for (String filePath : cppSliceProfilesInfo.keySet()) {
            SliceProfilesInfo profileInfo = cppSliceProfilesInfo.get(filePath);

            for (FunctionNamePos funcNamePos : profileInfo.functionNodes.keySet()) {
                Node functionNode = profileInfo.functionNodes.get(funcNamePos);
                String functionName = funcNamePos.getName();
                if (!functionName.toLowerCase().endsWith(jniFunctionSearchStr.toLowerCase())) {
                    continue;
                }
                List<ArgumentNamePos> functionArgs = XmlUtil.findFunctionParameters(functionNode);
                if (functionArgs.size() < 1 || jniArgPosIndex > functionArgs.size() - 1) {
                    continue;
                }
                NamePos arg = functionArgs.get(jniArgPosIndex);
                String sliceKey = arg.getName() + "%" + arg.getPos() + "%" + functionName + "%" + filePath;

                SliceProfile possibleSliceProfile = null;

                for (String cppProfileId : profileInfo.sliceProfiles.keySet()) {
                    SliceProfile cppProfile = profileInfo.sliceProfiles.get(cppProfileId);
                    if (cppProfileId.equals(sliceKey)) {
                        possibleSliceProfile = cppProfile;
                        break;
                    }
                }
                if (possibleSliceProfile == null) {
                    continue;
                }
                EnclNamePosTuple analyzedNamePosTuple = new EnclNamePosTuple(possibleSliceProfile.varName,
                    possibleSliceProfile.functionName, possibleSliceProfile.fileName,
                    possibleSliceProfile.definedPosition);
                if (!hasNoEdge(enclNamePosTuple, analyzedNamePosTuple)) {
                    continue;
                }
                if (isAnalyzedProfile(possibleSliceProfile)) {
                    continue;
                }
                analyzeSliceProfile(possibleSliceProfile, cppSliceProfilesInfo);
            }
        }
    }

    private boolean isFunctionOfGivenModifier(Node enclFunctionNode, String accessModifier) {
        List<Node> specifiers = XmlUtil.getNodeByName(enclFunctionNode, "specifier");
        for (Node specifier : specifiers) {
            String nodeName = specifier.getTextContent();
            if (accessModifier.equals(nodeName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNoEdge(EnclNamePosTuple sourceNamePosTuple, EnclNamePosTuple targetNamePosTuple) {
        if (sourceNamePosTuple.equals(targetNamePosTuple)) {
            return false;
        }
        if (!graph.containsVertex(sourceNamePosTuple)) {
            graph.addVertex(sourceNamePosTuple);
        }
        if (!graph.containsVertex(targetNamePosTuple)) {
            graph.addVertex(targetNamePosTuple);
        }
        if (graph.containsEdge(sourceNamePosTuple, targetNamePosTuple)) {
            return false;
        }

        graph.addEdge(sourceNamePosTuple, targetNamePosTuple);
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

    private boolean typeCheckFunctionSignature(FunctionNamePos functionNamePos, int argPosIndex,
                                                      String argTypeName, CFunction cFunction) {
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

}
