package org.overturetool.fmi.fmu.unifmu;

import com.fujitsu.vdmj.Settings;
import com.fujitsu.vdmj.ast.definitions.ASTBUSClassDefinition;
import com.fujitsu.vdmj.ast.definitions.ASTCPUClassDefinition;
import com.fujitsu.vdmj.ast.definitions.ASTClassList;
import com.fujitsu.vdmj.config.Properties;
import com.fujitsu.vdmj.lex.Dialect;
import com.fujitsu.vdmj.lex.LexException;
import com.fujitsu.vdmj.lex.LexTokenReader;
import com.fujitsu.vdmj.mapper.ClassMapper;
import com.fujitsu.vdmj.messages.VDMError;
import com.fujitsu.vdmj.messages.VDMWarning;
import com.fujitsu.vdmj.syntax.ClassReader;
import com.fujitsu.vdmj.syntax.ParserException;
import com.fujitsu.vdmj.tc.TCNode;
import com.fujitsu.vdmj.tc.definitions.TCClassList;
import com.fujitsu.vdmj.tc.definitions.TCExplicitOperationDefinition;
import com.fujitsu.vdmj.tc.lex.TCNameToken;
import com.fujitsu.vdmj.typechecker.ClassTypeChecker;
import com.fujitsu.vdmj.typechecker.TypeChecker;
import org.overturetool.fmi.vdm.annotations.ASTScalarVariableAnnotation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VdmjUtils {

    public static void init(){
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

    public static Map.Entry<ASTClassList, Boolean> parse(final List<Path> discoveredVdmrtFiles) {
        ASTClassList astClassList = new ASTClassList();
        try {
            astClassList.add(new ASTBUSClassDefinition());
            astClassList.add(new ASTCPUClassDefinition());
        } catch (ParserException | LexException e) {
            throw new RuntimeException(e);
        }
        boolean success = true;
        for (var p : discoveredVdmrtFiles) {
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

        return Map.entry(astClassList, success);
    }


    public static Map.Entry<Boolean, TCClassList> typeCheck(ASTClassList astClassList) throws Exception {
        TCClassList tcClassList = ClassMapper.getInstance(TCNode.MAPPINGS).init().convert(astClassList);


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
        return Map.entry(TypeChecker.getErrorCount() == 0, tcClassList);
    }
}
