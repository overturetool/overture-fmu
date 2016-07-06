package org.overture.fmi.annotation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Vector;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.destecs.core.parsers.ParserWrapper;
import org.overture.fmi.annotation.AnnotationParser.annotations_return;

public class AnnotationParserWrapper extends ParserWrapper<List<FmuAnnotation>>
{

	public List<FmuAnnotation> parse(File source, InputStream data)
			throws IOException
	{
		ANTLRInputStream input = new ANTLRInputStream(data);
		return internalParse(source, input);
	}

	protected List<FmuAnnotation> internalParse(File source, CharStream data)
			throws IOException
	{
		super.lexer = new AnnotationLexer(data);
		CommonTokenStream tokens = new CommonTokenStream(lexer);

		AnnotationParser thisParser = new AnnotationParser(tokens);
		parser = thisParser;

		((AnnotationLexer) lexer).enableErrorMessageCollection(true);
		thisParser.enableErrorMessageCollection(true);
		try
		{
			annotations_return annotations = thisParser.annotations();

			if (((AnnotationLexer) lexer).hasExceptions())
			{
				List<RecognitionException> exps = ((AnnotationLexer) lexer).getExceptions();
				addErrorsLexer(source, exps);
				return null;
			}

			if (thisParser.hasExceptions())
			{

				List<RecognitionException> exps = thisParser.getExceptions();
				addErrorsParser(source, exps);
			} else
			{
				List<FmuAnnotation> fmuAnnotations = new Vector<>();

				CommonTree tree = (CommonTree) annotations.getTree();

				if (tree != null && tree.getChildren() != null)
				{
					for (Object ann : tree.getChildren())
					{
						CommonTree annotation = (CommonTree) ann;

						String type = null;
						String name = null;

						if (annotation.getChildren() != null)
						{
							for (Object oc : annotation.getChildren())
							{
								CommonTree child = (CommonTree) oc;
								if (child.getText().equals("type"))
								{
									type = ((CommonTree) child.getChild(0)).getText();
								} else if (child.getText().equals("name"))
								{
									name = ((CommonTree) child.getChild(0)).getText();
								}
							}
						}

						fmuAnnotations.add(new FmuAnnotation(annotation, type, name));
					}
				}

				return fmuAnnotations;
			}
		} catch (RecognitionException errEx)
		{
			errEx.printStackTrace();
			addError(new ParseError(source, errEx.line, errEx.charPositionInLine, getErrorMessage(errEx, parser.getTokenNames())));
		}
		return null;
	}
}