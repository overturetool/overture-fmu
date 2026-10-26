package org.overturetool.fmi.fmu.unifmu.model;

import com.fujitsu.vdmj.ast.definitions.*;
import com.fujitsu.vdmj.ast.expressions.*;
import com.fujitsu.vdmj.ast.lex.*;
import com.fujitsu.vdmj.ast.patterns.ASTIdentifierPattern;
import com.fujitsu.vdmj.lex.LexLocation;
import com.fujitsu.vdmj.tc.definitions.*;
import com.fujitsu.vdmj.tc.expressions.*;
import com.fujitsu.vdmj.tc.lex.TCNameToken;
import com.fujitsu.vdmj.typechecker.*;
import org.overturetool.fmi.fmu.unifmu.*;
import org.overturetool.fmi.fmu.unifmu.protocol.FmiLogCallback;
import org.overturetool.fmi.fmu.unifmu.protocol.FmiModel;
import org.overturetool.fmi.fmu.unifmu.protocol.FmiProtocol;
import org.overturetool.fmi.vdm.annotations.ASTScalarVariableAnnotation;
import org.overturetool.fmi.vdm.annotations.TCScalarVariableAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.overturetool.fmi.fmu.unifmu.model.VdmRuntimeFmiModel.ok;

public abstract class VdmFmiModel extends FmiStateModel implements FmiModel {
    final static Logger logger = LoggerFactory.getLogger(VdmFmiModel.class);
    protected final List<Path> discoveredVdmrtFiles = new ArrayList<>();
    //this holds the list of parsed classes
    protected ASTClassList astClassList = new ASTClassList();
    protected List<ASTDefinition> astHardwareInterfaceValueDefinition = new ArrayList<>();
    //this holds the typechecked type list
    protected TCClassList tcClassList = new TCClassList();
    protected Function<ASTDefinition, LexNameToken> lexName = def -> {
        if (def instanceof ASTValueDefinition vdef) {
            if (vdef.pattern instanceof ASTIdentifierPattern p) {
                return p.name;
            }
        } else if (def instanceof ASTInstanceVariableDefinition ivdef) {
            return ivdef.name;
        }
        return null;
    };
    private FmiLogCallback fmiLogCallback;
    private boolean loggingOn;


    private final List<String> enabledLoggingCategories = new Vector<String>();
    private Map<String, TCDefinition> astHardwareInterfaceInstanceVariableDefinition = new HashMap<>();


    public VdmFmiModel(final List<Path> discoveredVdmrtFiles) {
        this.discoveredVdmrtFiles.addAll(discoveredVdmrtFiles);
        initialize();
    }

    public VdmFmiModel(String modelInstanceName, URI resourceLocation, Boolean visible, Boolean loggingOn) {
        try {
            logger.debug("Discovering VDMRT files in resource location: {}", resourceLocation);
            this.discoveredVdmrtFiles.addAll(VdmrtDiscoveryUtil.discover(Paths.get(resourceLocation.getPath())));
            logger.debug("Discovered VDMRT files: {}", this.discoveredVdmrtFiles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.loggingOn = loggingOn != null && loggingOn;
        this.modelName = modelInstanceName;
        initialize();
    }

    public VdmFmiModel() {
        this(VdmrtDiscoveryUtil.discoverForClass(VdmFmiModel.class, null));


    }

    protected void initialize() {
        VdmjUtils.init();
        try {
            init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        setInterfaceReferences(FmuPacker.getScalarVariables(tcClassList));
        setInitialOutputValues();

    }

    private void setInitialOutputValues() {
        for (var entry : interfaceReferences) {
            if (entry.causality() == FmuPacker.Causality.output) {
                for (var def : this.astHardwareInterfaceInstanceVariableDefinition.entrySet()) {
                    if (def.getKey().equals(entry.name())) {
                        if (def.getValue() instanceof TCInstanceVariableDefinition idef) {
                            if (idef.expression instanceof TCNewExpression nexp && nexp.args != null && nexp.args.size() == 1) {
                                System.out.println("Found match: " + def);
                                var value = nexp.args.getFirst();
                                Object val = null;
                                if (value instanceof TCRealLiteralExpression rle) {
                                    val = rle.value.value;
                                } else if (value instanceof TCIntegerLiteralExpression ile) {
                                    val = ile.value.value;
                                } else if (value instanceof TCBooleanLiteralExpression ble) {
                                    val = ble.value.value;
                                } else if (value instanceof TCStringLiteralExpression sle) {
                                    val = sle.value.value;
                                }

                                if (val != null) {
                                    outputs.put(entry.valueReference(), val);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected boolean parse() {
        var result = VdmjUtils.parse(discoveredVdmrtFiles);
        astClassList = result.getKey();
        boolean success = result.getValue();
        astHardwareInterfaceValueDefinition = new ArrayList<>();
        return success;
    }


    protected boolean typeCheck() throws Exception {
        var result = VdmjUtils.typeCheck(astClassList);
        boolean success = result.getKey();
        tcClassList = result.getValue();
        return success;

    }

    protected void init() throws Exception {

        if (parse() && typeCheck()) {
            System.out.println("Typechecked.");

            var hwDefOpt = astClassList.stream().filter(Objects::nonNull).filter(cdef -> cdef.name.name.equals("HardwareInterface")).findAny();

            if (!hwDefOpt.isPresent()) {
                return;
            }
            var hwDef = hwDefOpt.get();


            var hwiTypeDef = tcClassList.findType(new TCNameToken(null, "CLASS", "HardwareInterface"));

            var hwiDef = tcClassList.stream().filter(c -> c instanceof TCSystemDefinition).flatMap(s -> s.getDefinitions().stream().filter(d -> TypeComparator.compatible(hwiTypeDef.getType(), d.getType()))).findFirst();

            if (hwiDef.isEmpty()) {
                //we have no connection to the outside
                return;
            }

            var valueScalars = tcClassList.stream().flatMap(c -> c.getDefinitions().stream().filter(d -> d instanceof TCLocalDefinition vd && vd.valueDefinition != null && vd.valueDefinition.annotations.stream().anyMatch(a -> a instanceof TCScalarVariableAnnotation))).collect(Collectors.toMap(d -> d.name.getExplicit(true), Function.identity()));
            var variableScalars = tcClassList.stream().flatMap(c -> c.getDefinitions().stream().filter(d -> d instanceof TCInstanceVariableDefinition iv && iv.annotations.stream().anyMatch(a -> a instanceof TCScalarVariableAnnotation))).collect(Collectors.toMap(d -> "System." + hwiDef.get().name.getName() + "." + d.name.getExplicit(false), Function.identity()));

            this.astHardwareInterfaceInstanceVariableDefinition = variableScalars;
            this.astHardwareInterfaceValueDefinition = hwDef.definitions.stream().filter(def -> def.annotations != null && def.annotations.stream().anyMatch(an -> an instanceof ASTScalarVariableAnnotation) && valueScalars.values().stream().anyMatch(vd -> vd.name.getLex().equals(lexName.apply(def)))).toList();
        }
    }

    @Override
    public void setLogCallback(FmiLogCallback fmiLogCallback) {
        this.fmiLogCallback = fmiLogCallback;
    }

    public void fmiLog(LogCategory category, FmiProtocol.Fmi2Status status, String message) {
        if (fmiLogCallback != null) {
            fmiLogCallback.logFmiCallback(status, category.name, message);
        } else {
            System.out.println("[" + status + "] [" + category.name + "] " + message);
        }
    }

    public void fmiLog(LogCategory category, String message) {

        if (loggingOn) {
            switch (category) {
                case LogVdmErr:
                case LogError:
                    log(category.name(), FmiProtocol.Fmi2Status.Error, message);
                    break;

                case LogAll:
                case LogFmiCall:
                case LogProtocol:
                case LogVdmOut:
                default:
                    log(category.name(), FmiProtocol.Fmi2Status.OK, message);
                    break;
            }
        }

    }

    public void log(LogCategory category, FmiProtocol.Fmi2Status status, String message) {
        log(message, status, category.name);
    }

    @Override
    public void log(String message, FmiProtocol.Fmi2Status status, String category) {

        logger.trace("Log message Category: {}, Status: {}, Message: {}", category, status, message);
        if (enabledLoggingCategories.contains(category)) {
            if (fmiLogCallback != null) {
                message = message.trim();
                if (message.endsWith("\n")) {
                    message.substring(0, message.length() - 2);
                }
                logger.trace("Sending log message category: {}, data: {}", category, message);
                fmiLogCallback.logFmiCallback(status, category, message);
            } else {
                logger.warn("Log driver present: {}, is connected: {}, category: {}", false, false, category);
                System.out.println(message);
            }
        } else {
            logger.trace("Skipped log call back since category was not configured: {}", category);
        }
    }


    @Override
    public FmiProtocol.Fmi2Status fmi2EnterInitializationMode() {
        return FmiProtocol.Fmi2Status.OK;
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2ExitInitializationMode() {
        return FmiProtocol.Fmi2Status.OK;
    }

    @Override
    protected void setValueParameter(FmuPacker.ScalarVariable var, Object value) {

        for (var def : this.astHardwareInterfaceValueDefinition) {
            if (lexName.apply(def).name.equals(var.name().substring(var.name().indexOf(".") + 1))) {
                if (def instanceof ASTValueDefinition vdef) {
                    if (vdef.exp instanceof ASTNewExpression nexp) {
                        System.out.println("Found match: " + def);
                        nexp.args.clear();
                        LexLocation location = new LexLocation(new File("Runtime"), "Runtime", 0, 0, 0, 0);
                        if (value instanceof String sval) {
                            nexp.args.add(new ASTStringLiteralExpression(new LexStringToken(sval, location)));
                        } else if (value instanceof Double dval) {
                            nexp.args.add(new ASTRealLiteralExpression(new LexRealToken(dval, location)));
                        } else if (value instanceof Integer ival) {
                            nexp.args.add(new ASTIntegerLiteralExpression(new LexIntegerToken(ival, location)));
                        } else if (value instanceof Boolean bval) {
                            nexp.args.add(new ASTBooleanLiteralExpression(new LexBooleanToken(bval, location)));
                        }
                    }
                }
            }
        }

    }

    @Override
    public FmiProtocol.Fmi2Status fmi2SetupExperiment(double start_time, Double stop_time, Double tolerance) {
        return FmiProtocol.Fmi2Status.OK;
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2Reset() {
        return FmiProtocol.Fmi2Status.Discard;
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2Terminate() {
        return FmiProtocol.Fmi2Status.OK;
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2CancelStep() {
        return FmiProtocol.Fmi2Status.Discard;
    }

    @Override
    public Fmi2SerializeFmuStatePair fmi2SerializeFmuState() throws Exception {
        return new Fmi2SerializeFmuStatePair(FmiProtocol.Fmi2Status.Discard, new byte[0]);
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2DeserializeFmuState(byte[] bytes) throws Exception {
        return FmiProtocol.Fmi2Status.Discard;
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2SetDebugLogging(Iterable<String> categories, Boolean logging_on) {
        this.loggingOn = logging_on;
        if (categories != null)
            for (var category : categories) {
                logger.debug("Enabling logging for category: {}", category);
                enabledLoggingCategories.add(category);
            }
        return FmiProtocol.Fmi2Status.OK;
    }

    public enum LogCategory {

        LogAll("logAll"), LogFmiCall("logFmiCall"), LogProtocol("Protocol"), LogVdmOut(
                "VdmOut"), LogVdmErr("VdmErr"), LogError("logError");

        final String name;

        LogCategory(String name) {
            this.name = name;
        }

        public String categoryName() {
            return name;
        }
    }

}
