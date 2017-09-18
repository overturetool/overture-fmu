/*
 * #%~
 * Fmu import exporter
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
package org.overturetool.fmi.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.destecs.core.parsers.IError;
import org.overture.ast.definitions.AInstanceVariableDefinition;
import org.overture.ast.definitions.AValueDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.types.ACharBasicType;
import org.overture.ast.types.AClassType;
import org.overture.ast.types.ASeq1SeqType;
import org.overture.ast.types.ASeqSeqType;
import org.overture.ast.types.PType;
import org.overture.fmi.annotation.AnnotationParserWrapper;
import org.overture.fmi.annotation.FmuAnnotation;
import org.overture.fmi.annotation.RetainVdmCommentsFilter;
import org.overturetool.fmi.IProject;
import org.overturetool.fmi.IProject.MarkerType;

public class VdmAnnotationProcesser
{
	public Map<PDefinition, FmuAnnotation> collectAnnotatedDefinitions(
			IProject project, PrintStream out, PrintStream err)
	{

		Map<File, List<FmuAnnotation>> annotationsLexLinked = getSourceUnitAnnotations(project);

		Map<PDefinition, FmuAnnotation> definitionAnnotation = new HashMap<PDefinition, FmuAnnotation>();

		for (SClassDefinition cDef : project.getClasses())
		{
			File file = cDef.getName().getLocation().getFile();
			if (annotationsLexLinked.containsKey(file))
			{
				// class contains a def thats annotated, now find it
				for (PDefinition mDef : cDef.getDefinitions())
				{
					if (mDef instanceof AValueDefinition
							|| mDef instanceof AInstanceVariableDefinition)
					{

						for (FmuAnnotation annotation : annotationsLexLinked.get(file))
						{
							if (annotation.tree.token.getLine() == mDef.getLocation().getStartLine() - 1)
							{
								String name = mDef instanceof AValueDefinition ? ((AValueDefinition) mDef).getPattern()
										+ ""
										: mDef.getName().getName();
								if (annotation.name == null)
								{
									annotation.name = name;
								}
								out.println(String.format("Found annotated definition '%s' with type '%s' and name '%s'", mDef.getLocation().getModule()
										+ "." + name, annotation.type, annotation.name));

								if (mDef.getType() instanceof AClassType
										&& Arrays.asList(new String[] {
												"IntPort", "RealPort",
												"BoolPort", "StringPort" }).contains(((AClassType) mDef.getType()).getName().getName()))
								{
									definitionAnnotation.put(mDef, annotation);
								} else
								{
									err.println(String.format("Found annotated definition '%s' with type '%s' and name '%s'", mDef.getLocation().getModule()
											+ "." + name, annotation.type, annotation.name)
											+ " type not valid: "
											+ mDef.getType());
								}
								break;
							}
						}
					}

				}

			}
		}

		// Error reporting for unlinked definitions
		for (List<FmuAnnotation> annotationList : annotationsLexLinked.values())
		{
			for (FmuAnnotation commonTree : annotationList)
			{
				if (!definitionAnnotation.values().contains(commonTree))
				{

					for (Entry<File, List<FmuAnnotation>> entry : annotationsLexLinked.entrySet())
					{
						if (entry.getValue().contains(commonTree))
						{
							File unit = entry.getKey();
							project.addMarkser(unit, "Interface not linked to definition. The instanve-variable- or value- definition must be on the line below the annotation.", commonTree.tree.token.getLine() + 1, MarkerType.Error);
							err.println(String.format("Unlinked interface annotation: file %s:line %d ", unit.getName(), commonTree.tree.getLine()));
						}
					}
				}
			}
		}
		return definitionAnnotation;
	}

	public static boolean isStringType(PType type)
	{
		return type instanceof ASeqSeqType
				&& ((ASeqSeqType) type).getSeqof() instanceof ACharBasicType
				|| type instanceof ASeq1SeqType
				&& ((ASeq1SeqType) type).getSeqof() instanceof ACharBasicType;
	}

	private Map<File, List<FmuAnnotation>> getSourceUnitAnnotations(
			IProject project)
	{

		Map<File, List<FmuAnnotation>> annotations = new HashMap<File, List<FmuAnnotation>>();
		for (File unit : project.getSpecFiles())
		{
			project.deleteMarker(unit);
			AnnotationParserWrapper parser = new AnnotationParserWrapper();

			List<FmuAnnotation> result;
			try
			{
				result = parser.parse(unit, new RetainVdmCommentsFilter(new FileInputStream(unit), "--@"));

				if (parser.hasErrors())
				{
					for (IError error : parser.getErrors())
					{
						project.addMarkser(unit, error.getMessage(), error.getLine() + 1, MarkerType.Error);
					}
				}

				if (result != null && !result.isEmpty())
				{
					annotations.put(unit, result);
				}

			} catch (IOException e)
			{
				project.log(e);
			}

		}

		return annotations;
	}
}
