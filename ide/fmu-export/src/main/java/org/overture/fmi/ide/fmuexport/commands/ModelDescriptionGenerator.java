package org.overture.fmi.ide.fmuexport.commands;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.console.MessageConsoleStream;
import org.overture.ast.definitions.AInstanceVariableDefinition;
import org.overture.ast.definitions.ASystemClassDefinition;
import org.overture.ast.definitions.AValueDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.types.ABooleanBasicType;
import org.overture.ast.types.ARealNumericBasicType;
import org.overture.ast.types.PType;
import org.overture.ast.types.SNumericBasicType;
import org.overture.ast.util.definitions.ClassList;
import org.overture.fmi.annotation.FmuAnnotation;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.ide.core.resources.IVdmProject;
import org.overture.ide.core.resources.IVdmSourceUnit;
import org.overture.ide.ui.utility.PluginFolderInclude;

public class ModelDescriptionGenerator
{
	final static String scalarVariableTemplateInput = "<ScalarVariable name=\"%s\" valueReference=\"%d\" causality=\"%s\" variability=\"%s\">%s</ScalarVariable>";

	final static String scalarVariableTemplate = "<ScalarVariable name=\"%s\" valueReference=\"%d\" causality=\"%s\" variability=\"%s\" initial=\"%s\">%s</ScalarVariable>";
	final static String scalarVariableRealTypeTemplate = "<Real %s />";
	final static String scalarVariableIntegerTypeTemplate = "<Integer %s />";
	final static String scalarVariableBooleanTypeTemplate = "<Boolean %s />";
	final static String scalarVariableStringTypeTemplate = "<String %s />";

	final static String scalarVariableStartTemplate = "start=\"%s\"";

	final static String linkTemplate = "\t\t\t\t<link valueReference=\"%d\" name=\"%s\" />\n";

	public final static String INTERFACE_CLASSNAME = "HardwareInterface";
	public final static String INTERFACE_INSTANCE_NAME = "hwi";

	private final ASystemClassDefinition system;

	public static class ScalarInfo
	{
		public final PDefinition def;
		public final int index;
		public final FmuAnnotation annotation;

		public ScalarInfo(PDefinition def, int index, FmuAnnotation annotation)
		{
			this.def = def;
			this.index = index;
			this.annotation = annotation;
		}
	}

	public static class GeneratorInfo
	{
		public String modelDescription;
		public final Map<PDefinition, ScalarInfo> context = new HashMap<>();
		public int maxVariableReference;

	}

	public ModelDescriptionGenerator(ClassList classList,
			ASystemClassDefinition system)
	{
		this.system = system;
	}

	public GeneratorInfo generate(
			Map<PDefinition, FmuAnnotation> definitionAnnotation,
			IVdmProject project, MessageConsoleStream out,
			MessageConsoleStream err) throws AbortException, CoreException,
			IOException
	{
		GeneratorInfo info = new GeneratorInfo();
		boolean found = false;
		for (PDefinition def : system.getDefinitions())
		{
			if (def instanceof AInstanceVariableDefinition
					&& INTERFACE_INSTANCE_NAME.equals(def.getName().getName()))
			{
				found = true;
			}
		}

		if (!found)
		{
			String message = "Unable to locate " + system.getName().getName()
					+ "`" + INTERFACE_INSTANCE_NAME + " with type: '"
					+ INTERFACE_CLASSNAME + "'";
			err.println(message);
			throw new AbortException(message);
		}

		List<String> scalarVariables = new Vector<String>();
		Set<Integer> outputIndices = new HashSet<Integer>();
		int variableReference = 0;

		StringBuffer sbLinks = new StringBuffer();
		if (system != null)
		{
			for (Entry<PDefinition, FmuAnnotation> link : definitionAnnotation.entrySet())
			{
				// filter to system and HardwareInterface

				if (Arrays.asList(new String[] { "output", "input" }).contains(link.getValue().type)
						&& !INTERFACE_CLASSNAME.equals(link.getKey().getClassDefinition().getName().getName()))
				{
					err.println("WARNING: Skipping " + link.getValue().name
							+ " of type " + link.getValue().type + " -- "
							+ link.getKey().getName());
					continue;
				}

				int vr = variableReference++;

				info.context.put(link.getKey(), new ScalarInfo(link.getKey(), vr, link.getValue()));
				String scalarVariable = createScalarVariable(vr, link.getKey(), link.getValue(), sbLinks);
				scalarVariables.add(scalarVariable);
				if (link.getValue().type.equals("output"))
				{
					outputIndices.add(scalarVariables.size());
				}
			}

		}

		StringBuffer sbScalarVariables = new StringBuffer();

		for (int i = 0; i < scalarVariables.size(); i++)
		{
			String sv = scalarVariables.get(i);

			sbScalarVariables.append(String.format("\t\t<!-- Index %d -->\n", i + 1));
			sbScalarVariables.append("\t\t" + sv + "\n");
		}

		StringBuffer sbOutputs = new StringBuffer();

		if (outputIndices.size() > 0)
		{
			sbOutputs.append("\t<Outputs>\n");
			for (Integer integer : outputIndices)
			{
				sbOutputs.append(String.format("\t\t\t<Unknown index=\"%d\"  dependencies=\"\"/>", integer));
			}
			sbOutputs.append("\n\t</Outputs>\n");
		}

		StringBuffer sbSourceFiles = createSourceFileElements(project);

		final String modelDescriptionTemplate = PluginFolderInclude.readFile(IFmuExport.PLUGIN_ID, "includes/modelDescriptionTemplate.xml");

		String modelDescription = modelDescriptionTemplate.replace("<!-- {SCALARVARIABLES} -->", sbScalarVariables.toString());
		modelDescription = modelDescription.replace("<!-- {OUTPUTS} -->", sbOutputs.toString());
		modelDescription = modelDescription.replace("<!-- {LINKS} -->", sbLinks.toString());
		modelDescription = modelDescription.replace("<!-- {SourceFiles} -->", sbSourceFiles.toString());

		modelDescription = modelDescription.replace("{modelName}", project.getName());
		modelDescription = modelDescription.replace("{modelIdentifier}", project.getName());
		modelDescription = modelDescription.replace("{description}", "");
		modelDescription = modelDescription.replace("{author}", "");
		modelDescription = modelDescription.replace("{guid}", "{"
				+ java.util.UUID.randomUUID().toString() + "}");

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		String date = sdf.format(new Date());
		out.println("Setting generation data to: " + date);
		// Date d = sdf.parse(date);

		modelDescription = modelDescription.replace("{generationDateAndTime}", date);

		info.modelDescription = modelDescription;
		info.maxVariableReference = variableReference;
		return info;
	}

	protected StringBuffer createSourceFileElements(IVdmProject project)
			throws CoreException
	{
		StringBuffer sbSourceFiles = new StringBuffer();

		for (IVdmSourceUnit source : project.getSpecFiles())
		{
			sbSourceFiles.append(String.format("\t\t\t\t<File name=\"%s\" />\n", source.getFile().getProjectRelativePath()));
		}
		return sbSourceFiles;
	}

	private String createScalarVariable(int valueReference,
			PDefinition definition, FmuAnnotation annotation,
			StringBuffer sbLinks)
	{

		if (definition instanceof AValueDefinition)
		{

			// parameter generation
			AValueDefinition vDef = (AValueDefinition) definition;

			String name = annotation.name != null ? annotation.name
					: definition.getLocation().getModule() + "."
							+ vDef.getPattern();

			sbLinks.append(String.format(linkTemplate, valueReference, definition.getLocation().getModule()
					+ "." + vDef.getPattern()));

			String type = getType(vDef.getType(), "" + vDef.getExpression());
			return String.format(scalarVariableTemplate, name, valueReference, "parameter", "fixed", "exact", type);
		} else if (definition instanceof AInstanceVariableDefinition)
		{
			String name = annotation.name != null ? annotation.name
					: definition.getLocation().getModule() + "."
							+ definition.getName().getFullName();

			sbLinks.append(String.format(linkTemplate, valueReference, system.getName().getName()
					+ "."
					+ INTERFACE_INSTANCE_NAME
					+ "."
					+ definition.getName().getName()));
			if (annotation.type.equals("output"))
			{
				AInstanceVariableDefinition vDef = (AInstanceVariableDefinition) definition;
				String type = getType(vDef.getType(), null);
				return String.format(scalarVariableTemplate, name, valueReference, "output", "discrete", "calculated", type);

			} else if (annotation.type.equals("input"))
			{

				AInstanceVariableDefinition vDef = (AInstanceVariableDefinition) definition;
				PType rawType = vDef.getType();
				String type = getType(rawType, "" + vDef.getExpression());
				return String.format(scalarVariableTemplateInput, name, valueReference, "input", (rawType instanceof ARealNumericBasicType ? "continuous"
						: "discrete"), type);

			}
		}

		return null;
	}

	private String getType(PType type, String initial)
	{
		String typeTemplate = null;

		// TODO: check up on type chekcing here. This code does not work if a type def is used to add an invariant etc.
		if (type instanceof ARealNumericBasicType)
		{
			typeTemplate = scalarVariableRealTypeTemplate;
			if (initial != null && !initial.contains("."))
			{
				initial += ".0";
			}
		} else if (type instanceof ABooleanBasicType)
		{
			typeTemplate = scalarVariableBooleanTypeTemplate;

		} else if (type instanceof SNumericBasicType)
		{
			typeTemplate = scalarVariableIntegerTypeTemplate;

		}else if(VdmAnnotationProcesser.isStringType(type))
		{
			typeTemplate = scalarVariableStringTypeTemplate;
		}


		String start = initial != null ? String.format(scalarVariableStartTemplate, initial.replaceAll("\"","&quot;").replaceAll("\'","&apos;"))
				: "";
		return String.format(typeTemplate, start);
	}

}
