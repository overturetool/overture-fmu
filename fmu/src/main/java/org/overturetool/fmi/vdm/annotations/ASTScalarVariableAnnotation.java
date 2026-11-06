package org.overturetool.fmi.vdm.annotations;

import com.fujitsu.vdmj.ast.annotations.ASTAnnotation;
import com.fujitsu.vdmj.ast.lex.LexIdentifierToken;

public class ASTScalarVariableAnnotation extends ASTAnnotation {
    public ASTScalarVariableAnnotation(LexIdentifierToken name) {
        super(name);
    }
}
