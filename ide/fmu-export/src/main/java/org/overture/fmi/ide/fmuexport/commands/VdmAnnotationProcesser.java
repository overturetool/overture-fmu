package org.overture.fmi.ide.fmuexport.commands;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.destecs.core.parsers.IError;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.console.MessageConsoleStream;
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
import org.overture.fmi.ide.fmuexport.FmuExportPlugin;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.ide.core.ast.NotAllowedException;
import org.overture.ide.core.resources.IVdmProject;
import org.overture.ide.core.resources.IVdmSourceUnit;
import org.overture.ide.core.utility.FileUtility;

public class VdmAnnotationProcesser
{
	public Map<PDefinition, FmuAnnotation> collectAnnotatedDefinitions(
			IVdmProject project, MessageConsoleStream out,
			MessageConsoleStream err) throws NotAllowedException
	{
		Map<IVdmSourceUnit, List<FmuAnnotation>> annotations = getSourceUnitAnnotations(project);

		Map<File, List<FmuAnnotation>> annotationsLexLinked = new HashMap<File, List<FmuAnnotation>>();

		for (Entry<IVdmSourceUnit, List<FmuAnnotation>> entry : annotations.entrySet())
		{
			annotationsLexLinked.put(entry.getKey().getSystemFile(), entry.getValue());
		}

		Map<PDefinition, FmuAnnotation> definitionAnnotation = new HashMap<PDefinition, FmuAnnotation>();

		for (SClassDefinition cDef : project.getModel().getClassList())
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
										: mDef.getName().getFullName();
								out.println(String.format("Found annotated definition '%s' with type '%s' and name '%s'", mDef.getLocation().getModule()
										+ "." + name, annotation.type, annotation.name));

								if (mDef.getType() instanceof AClassType && Arrays.asList(new String[]{"IntPort","RealPort","BoolPort","StringPort"}).contains( ((AClassType)mDef.getType()).getName().getName()))
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
		for (List<FmuAnnotation> annotationList : annotations.values())
		{
			for (FmuAnnotation commonTree : annotationList)
			{
				if (!definitionAnnotation.values().contains(commonTree))
				{

					for (Entry<IVdmSourceUnit, List<FmuAnnotation>> entry : annotations.entrySet())
					{
						if (entry.getValue().contains(commonTree))
						{
							IVdmSourceUnit unit = entry.getKey();
							FileUtility.addMarker(unit.getFile(), "Interface not linked to definition. The instanve-variable- or value- definition must be on the line below the annotation.", commonTree.tree.token.getLine() + 1, IMarker.SEVERITY_WARNING, IFmuExport.PLUGIN_ID);
							err.println(String.format("Unlinked interface annotation: file %s:line %d ", unit.getFile().getName(), commonTree.tree.getLine()));
						}
					}
				}
			}
		}
		return definitionAnnotation;
	}

	public static boolean isStringType(PType type)
	{
		return (type instanceof ASeqSeqType && ((ASeqSeqType) type).getSeqof() instanceof ACharBasicType)
				|| (type instanceof ASeq1SeqType && ((ASeq1SeqType) type).getSeqof() instanceof ACharBasicType);
	}

	private Map<IVdmSourceUnit, List<FmuAnnotation>> getSourceUnitAnnotations(
			IVdmProject project)
	{

		Map<IVdmSourceUnit, List<FmuAnnotation>> annotations = new HashMap<IVdmSourceUnit, List<FmuAnnotation>>();
		try
		{
			for (IVdmSourceUnit unit : project.getSpecFiles())
			{
				FileUtility.deleteMarker(unit.getFile(), IMarker.PROBLEM, IFmuExport.PLUGIN_ID);
				AnnotationParserWrapper parser = new AnnotationParserWrapper();
				List<FmuAnnotation> result = parser.parse(unit.getSystemFile(), new RetainVdmCommentsFilter(unit.getFile().getContents(), "--@"));

				if (parser.hasErrors())
				{
					for (IError error : parser.getErrors())
					{
						FileUtility.addMarker(unit.getFile(), error.getMessage(), error.getLine() + 1, IMarker.SEVERITY_ERROR, IFmuExport.PLUGIN_ID);
					}
				}

				if (result != null && !result.isEmpty())
				{
					annotations.put(unit, result);
				}

			}
		} catch (CoreException e)
		{
			FmuExportPlugin.log("Error in annotation parsing", e);
		} catch (IOException e)
		{
			FmuExportPlugin.log("Error in annotation parsing", e);
		}

		return annotations;
	}

}
