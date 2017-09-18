/*
 * #%~
 * Annotation parser for FMU export
 * %%
 * Copyright (C) 2015 - 2017 Overture
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #~%
 */
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