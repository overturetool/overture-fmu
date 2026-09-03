package org.overturetool.fmi.fmu.unifmu.model;

import com.fujitsu.vdmj.Settings;
import com.fujitsu.vdmj.ast.definitions.*;
import com.fujitsu.vdmj.ast.expressions.*;
import com.fujitsu.vdmj.ast.lex.*;
import com.fujitsu.vdmj.ast.patterns.ASTIdentifierPattern;
import com.fujitsu.vdmj.config.Properties;
import com.fujitsu.vdmj.in.INNode;
import com.fujitsu.vdmj.in.definitions.INClassList;
import com.fujitsu.vdmj.lex.Dialect;
import com.fujitsu.vdmj.lex.LexException;
import com.fujitsu.vdmj.lex.LexLocation;
import com.fujitsu.vdmj.lex.LexTokenReader;
import com.fujitsu.vdmj.mapper.ClassMapper;
import com.fujitsu.vdmj.messages.VDMError;
import com.fujitsu.vdmj.messages.VDMWarning;
import com.fujitsu.vdmj.runtime.ClassInterpreter;
import com.fujitsu.vdmj.syntax.ClassReader;
import com.fujitsu.vdmj.syntax.ParserException;
import com.fujitsu.vdmj.tc.TCNode;
import com.fujitsu.vdmj.tc.definitions.*;
import com.fujitsu.vdmj.tc.lex.TCNameToken;
import com.fujitsu.vdmj.typechecker.*;
import com.fujitsu.vdmj.util.Utils;
import org.overturetool.fmi.fmu.unifmu.FmiLogCallback;
import org.overturetool.fmi.fmu.unifmu.FmiModel;
import org.overturetool.fmi.fmu.unifmu.FmiProtocol;
import org.overturetool.fmi.vdm.annotations.ASTScalarVariableAnnotation;
import org.overturetool.fmi.vdm.annotations.TCScalarVariableAnnotation;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class VdmModelInterpreter extends FmiStateModel implements FmiModel {

    private FmiLogCallback fmiLogCallback;
    private final List<Path> discoveredVdmrtFiles = new ArrayList<>();
    //this holds the list of parsed classes
    ASTClassList astClassList = new ASTClassList();
    List<ASTDefinition> astHardwareInterfaceValueDefinition = new ArrayList<>();
    //this holds the typechecked type list
    TCClassList tcClassList = new TCClassList();
    Function<ASTDefinition, LexNameToken> lexName = def -> {
        if (def instanceof ASTValueDefinition vdef) {
            if (vdef.pattern instanceof ASTIdentifierPattern p) {
                return p.name;
            }
        } else if (def instanceof ASTInstanceVariableDefinition ivdef) {
            return ivdef.name;
        }
        return null;
    };
    private ClassInterpreter interpreter=null;
    private Thread interpreterThread=null;


    public VdmModelInterpreter(final List<Path> discoveredVdmrtFiles) {
        this.discoveredVdmrtFiles.addAll(discoveredVdmrtFiles);
        initialize();
    }

    public VdmModelInterpreter() {
        this(VdmrtDiscoveryUtil.discoverForClass(VdmModelInterpreter.class, null));


    }

    void initialize() {
        Settings.prechecks = true;
        Settings.postchecks = true;
        Settings.invchecks = true;
        Settings.dynamictypechecks = true;
        Settings.measureChecks = true;
        Settings.annotations = true;
        Properties.annotations_debug = true;
        Properties.annotations_packages += ";" + ASTScalarVariableAnnotation.class.getPackageName();
        Properties.mapping_search_path = "/" + ASTScalarVariableAnnotation.class.getPackageName().replace('.', '/');
        boolean disableRtLog = true;
        boolean disableCoverage = false;
        boolean disableOptimization = false;

        Settings.dialect = Dialect.VDM_RT;
        Settings.filecharset = StandardCharsets.UTF_8;
        Settings.verbose = true;
    }

    boolean parse() {
        astClassList.clear();
        try {
            astClassList.add(new ASTBUSClassDefinition());
            astClassList.add(new ASTCPUClassDefinition());
        } catch (ParserException | LexException e) {
            throw new RuntimeException(e);
        }
        astHardwareInterfaceValueDefinition= new ArrayList<>();
        boolean success = true;
        for (var p : this.discoveredVdmrtFiles) {
            LexTokenReader ltr = new LexTokenReader(p.toFile(), Dialect.VDM_RT);
            ClassReader mr = new ClassReader(ltr);
            ASTClassList classes = mr.readClasses();

            if (mr.getErrorCount() > 0) {
                success = false;
                for (VDMError error : mr.getErrors()) {
                    System.out.println(error);
                }
            }

            if (mr.getWarningCount() > 0) {
                for (VDMWarning error : mr.getWarnings()) {
                    System.out.println(error);
                }
            }

            astClassList.addAll(classes);
        }

        return success;
    }


    boolean typeCheck() throws Exception {
        tcClassList = ClassMapper.getInstance(TCNode.MAPPINGS).init().convert(astClassList);
//        tcClassList.add(new TCCPUClassDefinition());
//        tcClassList.add(new TCBUSClassDefinition());
//        Field field = TCCPUClassDefinition.class.getDeclaredField("operationDefs");
//        field.setAccessible(true);
//        field.set(null, null);

        TypeChecker tc = new ClassTypeChecker(tcClassList);

        //TODO we should check that World`run() exists and is a valid function with no parameters
        try {
            tc.typeCheck();

            var worldTypeDef = tcClassList.findType(new TCNameToken(null, "CLASS", "World"));
            if (worldTypeDef == null) {
                throw new Exception("World class not found");
            }
            var runDef = worldTypeDef.getDefinitions().stream().filter(d -> d instanceof TCExplicitOperationDefinition op && op.name.getName().equals("run")).map(TCExplicitOperationDefinition.class::cast).findFirst();

            if(runDef.isEmpty())
            {
                throw new Exception("World.run() operation not found");
            }

            if(!runDef.get().getParamPatternList().isEmpty()&& !runDef.get().getParamPatternList().getFirst().isEmpty())
            {
                throw new Exception("World.run() operation should have no parameters");
            }
        } catch (Exception e) {
            System.err.println(e);
        }

        if (TypeChecker.getErrorCount() > 0) {
            for (VDMError error : TypeChecker.getErrors()) {
                System.out.println(error);
            }
        }

        if (TypeChecker.getWarningCount() > 0) {
            for (VDMWarning error : TypeChecker.getWarnings()) {
                System.out.println(error);
            }
        }
        return TypeChecker.getErrorCount() == 0;
    }

    class ModelVariable<T> {
        final String name;

        public ModelVariable(String name, int idx, Consumer<T> setter, Supplier<T> getter) {
            this.name = name;
            this.idx = idx;
            this.setter = setter;
            this.getter = getter;
        }

        final int idx;
        final Consumer<T> setter;
        final Supplier<T> getter;


    }

    void init() throws Exception {

        if (parse() && typeCheck()) {
            System.out.println("Typechecked.");

            var hwDefOpt = astClassList.stream().filter(Objects::nonNull).filter(def -> def instanceof ASTClassDefinition).filter(cdef -> cdef.name.name.equals("HardwareInterface")).findAny();

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
            var variableScalars = tcClassList.stream().flatMap(c -> c.getDefinitions().stream().filter(d -> d instanceof TCInstanceVariableDefinition iv && iv.annotations.stream().anyMatch(a -> a instanceof TCScalarVariableAnnotation))).collect(Collectors.toMap(d -> "System`" + hwiDef.get().name.getName() + "." + d.name.getExplicit(false), Function.identity()));


            this.astHardwareInterfaceValueDefinition = hwDef.definitions.stream().filter(def -> def.annotations != null && def.annotations.stream().anyMatch(an -> an instanceof ASTScalarVariableAnnotation) && valueScalars.values().stream().anyMatch(vd -> vd.name.getLex().equals(lexName.apply(def)))).toList();


            //find the @ScalarVariableAnnotations
//            for (var classDef : tcClassList) {
//                classDef.getDefinitions();
//            }



        }
    }


    @Override
    public void setLogCallback(FmiLogCallback fmiLogCallback) {
        this.fmiLogCallback = fmiLogCallback;
    }

    @Override
    public void log(String message, FmiProtocol.Fmi2Status status, String category) {
        if (fmiLogCallback != null) {
            fmiLogCallback.logFmiCallback(status, category, message);
        } else {
            System.out.println("[" + status + "] [" + category + "] " + message);
        }
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2DoStep(double current_time, double step_size, boolean noStepPrior) {
        return null;
    }


    @Override
    public FmiProtocol.Fmi2Status fmi2EnterInitializationMode() {
        return null;
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2ExitInitializationMode() {
        try {
            typeCheck();
            System.out.println("fmi2ExitInitializationMode");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        TCClassList checkedClasses = tcClassList;
        long before = System.currentTimeMillis();

        try {
            INClassList inClassList  = ClassMapper.getInstance(INNode.MAPPINGS).init().convert(checkedClasses);
            Utils.mapperStats(before, INNode.MAPPINGS);
            this.interpreter = new ClassInterpreter(inClassList, checkedClasses);
            this.interpreter.init();
            this.interpreterThread=new Thread(() -> {
                try {
                    interpreter.execute("new World().run()");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            this.interpreterThread.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }



        return null;
    }

    @Override
    protected void setValueParameter(FmiStateModel.ModelVariable var, Object value) {

        for (var def : this.astHardwareInterfaceValueDefinition) {
            if (lexName.apply(def).name.equals(var.name.substring(var.name.indexOf(".") + 1))) {
                if (def instanceof ASTValueDefinition vdef) {
                    if (vdef.exp instanceof ASTNewExpression nexp) {
                        System.out.println("Found match: " + def);
                        nexp.args.clear();
                        LexLocation location = new LexLocation(new File("Runtime"), "Runtime", 0, 0, 0, 0);
                        if (value instanceof String sval) {
                            nexp.args.add(new ASTStringLiteralExpression(new LexStringToken(sval, location)));
                        }else if(value instanceof Double dval){
                            nexp.args.add(new ASTRealLiteralExpression(new LexRealToken(dval, location)));
                        }else if(value instanceof Integer ival)
                        {
                            nexp.args.add(new ASTIntegerLiteralExpression(new LexIntegerToken(ival, location)));
                        }else if(value instanceof Boolean bval)
                        {
                            nexp.args.add(new ASTBooleanLiteralExpression(new LexBooleanToken(bval, location)));
                        }
                    }
                }
            }
        }

    }

    @Override
    public FmiProtocol.Fmi2Status fmi2SetupExperiment(double start_time, Double stop_time, Double tolerance) {
        return null;
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2Reset() {
        return FmiProtocol.Fmi2Status.Discard;
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2Terminate() {
        return null;
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
        return null;
    }
}
