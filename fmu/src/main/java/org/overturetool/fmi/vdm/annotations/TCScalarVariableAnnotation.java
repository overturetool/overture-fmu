package org.overturetool.fmi.vdm.annotations;

import com.fujitsu.vdmj.ast.lex.LexBooleanToken;
import com.fujitsu.vdmj.tc.annotations.TCAnnotation;
import com.fujitsu.vdmj.tc.definitions.TCClassDefinition;
import com.fujitsu.vdmj.tc.definitions.TCDefinition;
import com.fujitsu.vdmj.tc.definitions.TCInstanceVariableDefinition;
import com.fujitsu.vdmj.tc.definitions.TCValueDefinition;
import com.fujitsu.vdmj.tc.expressions.TCBooleanLiteralExpression;
import com.fujitsu.vdmj.tc.expressions.TCExpression;
import com.fujitsu.vdmj.tc.expressions.TCExpressionList;
import com.fujitsu.vdmj.tc.expressions.TCVariableExpression;
import com.fujitsu.vdmj.tc.lex.TCIdentifierToken;
import com.fujitsu.vdmj.tc.lex.TCNameToken;
import com.fujitsu.vdmj.tc.modules.TCModule;
import com.fujitsu.vdmj.tc.statements.TCForIndexStatement;
import com.fujitsu.vdmj.tc.statements.TCStatement;
import com.fujitsu.vdmj.tc.statements.TCWhileStatement;
import com.fujitsu.vdmj.tc.types.TCQuoteType;
import com.fujitsu.vdmj.tc.types.TCType;
import com.fujitsu.vdmj.tc.types.TCTypeSet;
import com.fujitsu.vdmj.tc.types.TCUnionType;
import com.fujitsu.vdmj.typechecker.Environment;
import com.fujitsu.vdmj.typechecker.NameScope;
import com.fujitsu.vdmj.typechecker.TypeComparator;

import java.util.List;

import static com.fujitsu.vdmj.typechecker.TypeChecker.detail2;

public class TCScalarVariableAnnotation extends TCAnnotation {

    public TCScalarVariableAnnotation(TCIdentifierToken name, TCExpressionList args) {
        super(name, args);
    }

    void reportNotAllowedHere() {
        name.report(6006, "@ScalarVariable only applies to HardwareInterface variables");
    }

    @Override
    public void tcBefore(TCDefinition def, Environment env, NameScope scope) {
        if (!def.classDefinition.name.getClassName().getName().equals("HardwareInterface")) {

            reportNotAllowedHere();
        }

        if (args.size() != 2) {
            name.report(6007, "@ScalarVariable must have exactly 3 arguments");
        }

        List<TCType> expectedTypes = null;

        if (def instanceof TCValueDefinition) {
            expectedTypes = List.of(
                    new TCUnionType(null, new TCTypeSet(new TCQuoteType(null, "parameter"))),
                    new TCUnionType(null, new TCTypeSet(new TCQuoteType(null, "constant"), new TCQuoteType(null, "fixed"), new TCQuoteType(null, "local")))
            );
        } else if (def instanceof TCInstanceVariableDefinition) {
            expectedTypes = List.of(
                    new TCUnionType(null, new TCTypeSet(new TCQuoteType(null, "input"), new TCQuoteType(null, "output"), new TCQuoteType(null, "local"))),
                    new TCUnionType(null, new TCTypeSet(new TCQuoteType(null, "discrete"), new TCQuoteType(null, "continuous")))
            );
        }


        if (expectedTypes != null && expectedTypes.size() == args.size())
            for (TCExpression arg : args) {
                arg.typeCheck(env, null, scope, null);
                var index = args.indexOf(arg);
                var expectedType = expectedTypes.get(index);

                if (!TypeComparator.isSubType(arg.getType(), expectedType, true)) {
                    arg.report(6008, "@ScalarVariable argument "+(index+1)+" is not of type " + expectedType);
                }
            }

    }

    @Override
    public void tcAfter(TCDefinition def, TCType type, Environment env, NameScope scope) {
        super.tcAfter(def, type, env, scope);
    }

    @Override
    public void tcBefore(TCModule module) {
        reportNotAllowedHere();
    }

    @Override
    public void tcBefore(TCClassDefinition clazz) {
        reportNotAllowedHere();
    }

    @Override
    public void tcBefore(TCExpression exp, Environment env, NameScope scope) {
        reportNotAllowedHere();
    }

    @Override
    public void tcBefore(TCStatement stmt, Environment env, NameScope scope) {
        reportNotAllowedHere();
//        if (!isLoop(stmt))
//        {
//            name.report(6006, "@LoopInvariant only applies to loop statements");
//        }
//        else if (args.size() != 1 && args.size() != 2)
//        {
//            name.report(6007, "@LoopInvariant args: (<boolean expression> [, <ghost>])");
//            args.clear();
//            args.add(new TCBooleanLiteralExpression(LexBooleanToken.TRUE));		// Avoids later errs
//        }
//        else if (args.size() == 2)
//        {
//            if (stmt instanceof TCWhileStatement || stmt instanceof TCForIndexStatement)
//            {
//                args.get(1).warning(6007, "@LoopInvariant ghost not used");
//            }
//            else if (args.get(1) instanceof TCVariableExpression)
//            {
//                TCVariableExpression variable = (TCVariableExpression)args.get(1);
//
//                if (env.findName(variable.name, scope) != null)
//                {
//                    args.get(1).report(6007, "@LoopInvariant ghost already in scope");
//                }
//
//                ghostName = variable.name;
//            }
//            else
//            {
//                args.get(1).report(6007, "@LoopInvariant ghost must be a name");
//            }
//        }
//
//        // Further checks are performed across the collection of all LoopInvariants on
//        // a particular loop statement. See TCLoopAnnotations.typeCheck().
    }
}
