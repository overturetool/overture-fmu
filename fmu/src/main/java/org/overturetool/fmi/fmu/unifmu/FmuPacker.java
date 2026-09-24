package org.overturetool.fmi.fmu.unifmu;

import com.fujitsu.vdmj.tc.definitions.*;
import com.fujitsu.vdmj.tc.expressions.TCNewExpression;
import com.fujitsu.vdmj.tc.lex.TCNameToken;
import com.fujitsu.vdmj.tc.types.TCClassType;
import com.fujitsu.vdmj.typechecker.TypeComparator;
import org.overturetool.fmi.vdm.annotations.TCScalarVariableAnnotation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FmuPacker {

    public enum Type {
        Real, Integer, Boolean, String
    }

    public enum Variability {
        constant, discrete, continuous, tunable
    }

    public enum Causality {
        parameter, calculatedParameter, input, output, local, independent
    }

    public record ScalarVariable(int valueReference, String name, Type type, Variability variability,
                                 Causality causality, TCDefinition definition, Object initialValue) {

    }

    private static final String MODEL_DESCRIPTION_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fmiModelDescription fmiVersion="2.0"
                                 modelName="%s"
                                 guid="%s"
                                 generationTool="overture-fmu"
                                 generationDateAndTime="%s"
                                 variableNamingConvention="flat"
                                 numberOfEventIndicators="0">
              <CoSimulation modelIdentifier="%s"
                            needsExecutionTool="true"
                            canHandleVariableCommunicationStepSize="true"
                            canInterpolateInputs="false"
                            maxOutputDerivativeOrder="0"
                            canRunAsynchronuously="false"
                            canBeInstantiatedOnlyOncePerProcess="false"
                            canNotUseMemoryManagementFunctions="true"
                            canGetAndSetFMUstate="false"
                            canSerializeFMUstate="false"
                            providesDirectionalDerivative="false"/>
              <ModelVariables>
            %s
              </ModelVariables>
              <ModelStructure>
            %s
              </ModelStructure>
            </fmiModelDescription>
            """;
    private static final String MAIN_CLASS = "org.overturetool.fmi.fmu.unifmu.protocol.Main";
    private static final String MODEL_IMPL_CLASS = "org.overturetool.fmi.fmu.unifmu.model.VdmRuntimeFmiModel";

    public static List<ScalarVariable> getScalarVariables(final List<Path> discoveredVdmrtFiles) throws Exception {
        VdmjUtils.init();
        var res = VdmjUtils.parse(discoveredVdmrtFiles);
        if (res.getValue()) {
            var astClassList = res.getKey();
            var tcRes = VdmjUtils.typeCheck(astClassList);
            return getScalarVariables(tcRes.getValue());
        }
        return null;
    }/**/


    public static List<ScalarVariable> getScalarVariables(TCClassList tcClassList) {
        var hwiTypeDef = tcClassList.findType(new TCNameToken(null, "CLASS", "HardwareInterface"));

        if (hwiTypeDef == null) {
            return null;
        }

        var scalarVariables = new ArrayList<ScalarVariable>();

        // Collect value definitions with @ScalarVariable annotations (TCLocalDefinition.valueDefinition)
        var valueDefsList = tcClassList.stream().flatMap(c -> c.getDefinitions().stream()).filter(d -> d instanceof TCLocalDefinition).filter(d -> {
            TCLocalDefinition ld = (TCLocalDefinition) d;
            return ld.valueDefinition != null && ld.valueDefinition.annotations != null && ld.valueDefinition.annotations.stream().anyMatch(a -> a instanceof TCScalarVariableAnnotation);
        }).map(d -> Map.entry(d, d.location.file.getAbsolutePath())).toList();

        // Collect instance variable definitions with @ScalarVariable annotations (TCInstanceVariableDefinition)
        var instanceVarsList = tcClassList.stream().flatMap(c -> c.getDefinitions().stream()).filter(d -> d instanceof TCInstanceVariableDefinition).filter(def -> def.annotations != null && def.annotations.stream().anyMatch(a -> a instanceof TCScalarVariableAnnotation)).map(def -> Map.entry(def, def.location.file.getAbsolutePath())).toList();

        // Combine both lists
        var annotatedDefs = new ArrayList<Map.Entry<TCDefinition, String>>();
        annotatedDefs.addAll(valueDefsList);
        annotatedDefs.addAll(instanceVarsList);

        // Sort by file path, then by line number
        annotatedDefs.sort((e1, e2) -> {
            int fileCompare = e1.getValue().compareTo(e2.getValue());
            if (fileCompare != 0) return fileCompare;
            return Integer.compare(e1.getKey().location.startLine, e2.getKey().location.startLine);
        });
        var systemClass = tcClassList.stream().filter(c -> c instanceof TCSystemDefinition).findFirst();
        var systemClassHardwareInterfaceVar = tcClassList.stream().filter(c -> c instanceof TCSystemDefinition).findFirst().flatMap(def -> def.definitions.stream().filter(d -> d instanceof TCInstanceVariableDefinition && d.getType() instanceof TCClassType && ((TCClassType) d.getType()).classdef == hwiTypeDef).findFirst());
        if (systemClass.isEmpty() || systemClassHardwareInterfaceVar.isEmpty()) {
            return Collections.emptyList();
        }

            // Create scalar variables from sorted definitions
            int valueReference = 0;
            for (var entry : annotatedDefs) {
                TCDefinition def = entry.getKey();
                Type type = inferType(def.getType());
                Variability variability = Variability.continuous;
                Causality causality = Causality.local;
                Object initialValue = null;

                var annotations = def instanceof TCLocalDefinition ? ((TCLocalDefinition) def).valueDefinition.annotations : def.annotations;
                // Extract causality and variability from annotation if available
                if (annotations != null) {
                    for (var ann : annotations) {
                        if (ann instanceof TCScalarVariableAnnotation scalarAnn) {
                            if (!scalarAnn.args.isEmpty()) {
                                causality = parseCausality(scalarAnn.args.getFirst());
                            }
                            if (scalarAnn.args.size() >= 2) {
                                variability = parseVariability(scalarAnn.args.get(1));
                            }
                        }
                    }
                }
                String name = null;
                switch (causality) {
                    case parameter, calculatedParameter -> {
                        name = def.name.getExplicit(true).toString().replace("`", ".");
                    }
                    case input, output, local, independent -> {
                        name = systemClass.get().name.getName() + "." + systemClassHardwareInterfaceVar.get().name.getName() + "." + def.name.getExplicit(false).toString().replace("`", ".");
                    }
                }

                if(entry.getKey() instanceof TCLocalDefinition  vdef )
                {
                    if(vdef.valueDefinition != null && vdef.valueDefinition.exp instanceof TCNewExpression newExpr && newExpr.args != null && !newExpr.args.isEmpty())
                    {
                        initialValue = newExpr.args.getFirst().toString();
                    }

                }else if(entry.getKey() instanceof TCInstanceVariableDefinition idef)
                {
                    if(idef.expression != null && idef.expression instanceof TCNewExpression newExpr && newExpr.args != null && !newExpr.args.isEmpty())
                    {
                        initialValue = newExpr.args.getFirst().toString();
                    }
                }


                scalarVariables.add(new ScalarVariable(valueReference++, name, type, variability, causality, def, initialValue));
            }

            return scalarVariables;
        }

        private static Type inferType (com.fujitsu.vdmj.tc.types.TCType vdmType){
            if (vdmType.toString().contains("real")) return Type.Real;
            if (vdmType.toString().contains("int")) return Type.Integer;
            if (vdmType.toString().contains("bool")) return Type.Boolean;
            if (vdmType.toString().contains("seq of char")) return Type.String;
            return Type.Real;
        }

        private static Causality parseCausality (com.fujitsu.vdmj.tc.expressions.TCExpression expr){
            String val = expr.toString().toLowerCase();
            if (val.contains("input")) return Causality.input;
            if (val.contains("output")) return Causality.output;
            if (val.contains("parameter")) return Causality.parameter;
            if (val.contains("calculatedparameter")) return Causality.calculatedParameter;
            if (val.contains("local")) return Causality.local;
            return Causality.local;
        }

        private static Variability parseVariability (com.fujitsu.vdmj.tc.expressions.TCExpression expr){
            String val = expr.toString().toLowerCase();
            if (val.contains("discrete")) return Variability.discrete;
            if (val.contains("continuous")) return Variability.continuous;
            if (val.contains("constant")) return Variability.constant;
            if (val.contains("tunable")) return Variability.tunable;
            return Variability.continuous;
        }

        public static String createModelDescription(String modelName, List<ScalarVariable> scalarVariables) {
            Objects.requireNonNull(modelName, "modelName");
            List<ScalarVariable> safeScalars = Objects.requireNonNullElseGet(scalarVariables, List::of);

            String scalarXml = safeScalars.stream()
                    .map(FmuPacker::toScalarVariableXml)
                    .collect(Collectors.joining("\n"));

            String outputsXml = safeScalars.stream()
                    .filter(scalar -> scalar.causality() == Causality.output)
                    .map(scalar -> "    <Outputs>\n      <Unknown index=\"" + (scalar.valueReference() + 1) + "\" dependencies=\"\"/>\n    </Outputs>")
                    .findFirst()
                    .map(first -> safeScalars.stream()
                            .filter(scalar -> scalar.causality() == Causality.output)
                            .map(scalar -> "      <Unknown index=\"" + (scalar.valueReference() + 1) + "\" dependencies=\"\"/>")
                            .collect(Collectors.joining("\n", "    <Outputs>\n", "\n    </Outputs>")))
                    .orElse("");

            return MODEL_DESCRIPTION_TEMPLATE.formatted(
                    escapeXml(modelName),
                    UUID.randomUUID(),
                    Instant.now().toString(),
                    escapeXml(modelName),
                    scalarXml.isEmpty() ? "" : scalarXml + "\n",
                    outputsXml
            );
        }

        public static Path packageFmu(Path vdmModelPath, Path outputDirectory, String fmuName) throws Exception {
            Path normalizedModelPath = vdmModelPath.toAbsolutePath().normalize();
            Path normalizedOutputDir = outputDirectory.toAbsolutePath().normalize();
            Files.createDirectories(normalizedOutputDir);

            List<Path> discoveredVdmrtFiles = discoverModelFiles(normalizedModelPath);
            List<ScalarVariable> scalarVariables = getScalarVariables(discoveredVdmrtFiles);
            String modelDescription = createModelDescription(fmuName, scalarVariables);

            Path archive = normalizedOutputDir.resolve(fmuName.endsWith(".fmu") ? fmuName : fmuName + ".fmu");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                addDirectoryEntry(zip, "binaries/");
                addDirectoryEntry(zip, "resources/");
                addFile(zip, "modelDescription.xml", modelDescription.getBytes(StandardCharsets.UTF_8));

                copyDirectoryToZip(zip, resolveProjectResource("binaries"), "binaries/");
                copyModelResources(zip, normalizedModelPath, "resources/");
                addFile(zip, "resources/log4j2.xml", Files.readAllBytes(resolveProjectResource("log4j2.xml")));
                RuntimeArtifact runtimeArtifact = copyRuntimeArtifact(zip, "resources/");
                addFile(zip, "resources/launch.toml", createLaunchToml(runtimeArtifact).getBytes(StandardCharsets.UTF_8));
            }

            return archive;
        }

        public static void main(String[] args) throws Exception {
            if (args.length != 3) {
                throw new IllegalArgumentException("Usage: FmuPacker <vdm-model-path> <output-directory> <fmu-name>");
            }

            Path archive = packageFmu(Paths.get(args[0]), Paths.get(args[1]), args[2]);
            System.out.println("Created FMU: " + archive);
        }

        private static List<Path> discoverModelFiles(Path modelPath) throws IOException {
            if (Files.isDirectory(modelPath)) {
                try (Stream<Path> paths = Files.walk(modelPath)) {
                    return paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vdmrt"))
                            .sorted()
                            .toList();
                }
            }
            if (Files.isRegularFile(modelPath)) {
                return List.of(modelPath);
            }
            throw new IOException("VDM model path does not exist: " + modelPath);
        }

        private static String toScalarVariableXml(ScalarVariable scalar) {
            String typeXml = switch (scalar.type()) {
                case Real -> "      <Real" + startAttribute(scalar, scalar.initialValue+"") + "/>";
                case Integer -> "      <Integer" + startAttribute(scalar, scalar.initialValue+"") + "/>";
                case Boolean -> "      <Boolean" + startAttribute(scalar, scalar.initialValue+"") + "/>";
                case String -> "      <String" + startAttribute(scalar, scalar.initialValue+"") + "/>";
            };

            String initial = scalar.causality() == Causality.output ? " initial=\"calculated\"" :
                    (scalar.causality() == Causality.parameter || scalar.causality() == Causality.calculatedParameter ? " initial=\"exact\"" : "");

            return """
                        <ScalarVariable name="%s" valueReference="%d" causality="%s" variability="%s"%s>
                %s
                        </ScalarVariable>""".formatted(
                    escapeXml(scalar.name()),
                    scalar.valueReference(),
                    scalar.causality(),
                    scalar.variability(),
                    initial,
                    typeXml
            );
        }

        private static String startAttribute(ScalarVariable scalar, String defaultValue) {
            return switch (scalar.causality()) {
                case input, parameter, calculatedParameter -> " start=\"" + escapeXml(defaultValue) + "\" ";
                default -> " ";
            };
        }

        private static String escapeXml(String value) {
            return value
                    .replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }

        private static Path resolveProjectResource(String relativePath) {
            Path moduleRoot = Paths.get("").toAbsolutePath().normalize();
            if (Files.exists(moduleRoot.resolve("src/main/resources").resolve(relativePath))) {
                return moduleRoot.resolve("src/main/resources").resolve(relativePath);
            }
            return moduleRoot.resolve("fmu").resolve("src/main/resources").resolve(relativePath);
        }

        private static void copyModelResources(ZipOutputStream zip, Path source, String zipPrefix) throws IOException {
            if (Files.isDirectory(source)) {
                try (Stream<Path> paths = Files.walk(source)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        String entryName = zipPrefix + source.relativize(path).toString().replace('\\', '/');
                        addFile(zip, entryName, Files.readAllBytes(path));
                    }
                }
                return;
            }

            addFile(zip, zipPrefix + source.getFileName(), Files.readAllBytes(source));
        }

        private static void copyDirectoryToZip(ZipOutputStream zip, Path sourceDir, String zipPrefix) throws IOException {
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                for (Path path : paths.toList()) {
                    if (path.equals(sourceDir)) {
                        continue;
                    }
                    String entryName = zipPrefix + sourceDir.relativize(path).toString().replace('\\', '/');
                    if (Files.isDirectory(path)) {
                        addDirectoryEntry(zip, entryName + "/");
                    } else {
                        addFile(zip, entryName, Files.readAllBytes(path));
                    }
                }
            }
        }

        private static RuntimeArtifact copyRuntimeArtifact(ZipOutputStream zip, String zipPrefix) throws IOException {
            Path moduleRoot = Paths.get("").toAbsolutePath().normalize();
            Path targetDir = Files.exists(moduleRoot.resolve("target")) ? moduleRoot.resolve("target") : moduleRoot.resolve("fmu").resolve("target");
            Path fatJar = targetDir.resolve("vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar");
            if (Files.exists(fatJar)) {
                String entryName = zipPrefix + fatJar.getFileName();
                addFile(zip, entryName, Files.readAllBytes(fatJar));
                return new RuntimeArtifact(RuntimeArtifactType.JAR, entryName);
            }

            Path classesDir = targetDir.resolve("classes");
            if (!Files.isDirectory(classesDir)) {
                throw new IOException("No runtime artifact found in " + targetDir);
            }
            copyDirectoryToZip(zip, classesDir, zipPrefix + "classes/");
            return new RuntimeArtifact(RuntimeArtifactType.CLASSES, zipPrefix + "classes/");
        }

        private static String createLaunchToml(RuntimeArtifact runtimeArtifact) {
            return switch (runtimeArtifact.type()) {
                case JAR -> """
                        linux = ["java", "-Dlog4j.configurationFile=resources/log4j2.xml", "-Dfmu.model.impl=%s", "-cp", "%s", "%s"]
                        macos = ["java", "-Dlog4j.configurationFile=resources/log4j2.xml", "-Dfmu.model.impl=%s", "-cp", "%s", "%s"]
                        windows = ["java", "-Dlog4j.configurationFile=resources/log4j2.xml", "-Dfmu.model.impl=%s", "-cp", "%s", "%s"]
                        """.formatted(MODEL_IMPL_CLASS, runtimeArtifact.path(), MAIN_CLASS,
                        MODEL_IMPL_CLASS, runtimeArtifact.path(), MAIN_CLASS,
                        MODEL_IMPL_CLASS, runtimeArtifact.path(), MAIN_CLASS);
                case CLASSES -> """
                        linux = ["java", "-Dlog4j.configurationFile=resources/log4j2.xml", "-Dfmu.model.impl=%s", "-cp", "%s", "%s"]
                        macos = ["java", "-Dlog4j.configurationFile=resources/log4j2.xml", "-Dfmu.model.impl=%s", "-cp", "%s", "%s"]
                        windows = ["java", "-Dlog4j.configurationFile=resources/log4j2.xml", "-Dfmu.model.impl=%s", "-cp", "%s", "%s"]
                        """.formatted(MODEL_IMPL_CLASS, runtimeArtifact.path(), MAIN_CLASS,
                        MODEL_IMPL_CLASS, runtimeArtifact.path(), MAIN_CLASS,
                        MODEL_IMPL_CLASS, runtimeArtifact.path(), MAIN_CLASS);
            };
        }

        private static void addDirectoryEntry(ZipOutputStream zip, String entryName) throws IOException {
            ZipEntry entry = new ZipEntry(entryName);
            zip.putNextEntry(entry);
            zip.closeEntry();
        }

        private static void addFile(ZipOutputStream zip, String entryName, byte[] content) throws IOException {
            ZipEntry entry = new ZipEntry(entryName);
            zip.putNextEntry(entry);
            zip.write(content);
            zip.closeEntry();
        }

        private enum RuntimeArtifactType {
            JAR, CLASSES
        }

        private record RuntimeArtifact(RuntimeArtifactType type, String path) {
        }
    }
