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
package org.overturetool.fmi.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.commons.io.IOUtils;
import org.overture.ast.definitions.AInstanceVariableDefinition;
import org.overture.ast.definitions.ASystemClassDefinition;
import org.overture.ast.definitions.AValueDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.expressions.ANewExp;
import org.overture.ast.expressions.AStringLiteralExp;
import org.overture.ast.expressions.PExp;
import org.overture.ast.types.ABooleanBasicType;
import org.overture.ast.types.AClassType;
import org.overture.ast.types.ARealNumericBasicType;
import org.overture.ast.types.PType;
import org.overture.ast.types.SNumericBasicType;
import org.overture.ast.util.definitions.ClassList;
import org.overture.fmi.annotation.FmuAnnotation;
import org.overturetool.fmi.AbortException;
import org.overturetool.fmi.IProject;
import org.overturetool.fmi.Main;
import org.overturetool.fmi.util.VdmAnnotationProcesser;

public class ModelDescriptionGenerator
{
	final static String scalarVariableTemplateInput = "<ScalarVariable name=\"%s\" valueReference=\"%d\" causality=\"%s\" variability=\"%s\">%s</ScalarVariable>";

	final static String scalarVariableTemplate = "<ScalarVariable name=\"%s\" valueReference=\"%d\" causality=\"%s\" variability=\"%s\" initial=\"%s\">%s</ScalarVariable>";
	final static String scalarVariableRealTypeTemplate = "<Real %s />";
	final static String scalarVariableIntegerTypeTemplate = "<Integer %s />";
	final static String scalarVariableBooleanTypeTemplate = "<Boolean %s />";
	final static String scalarVariableStringTypeTemplate = "<String %s />";

	final static String scalarVariableStartTemplate = "start=\"%s\"";

	final static String linkTemplate = "\t\t\t\t<link valueReference=\"%d\" name=\"%s.value\" />\n";

	public final static String INTERFACE_CLASSNAME = "HardwareInterface";
	public final static String INTERFACE_INSTANCE_NAME = "hwi";

	private final ASystemClassDefinition system;
	Date generationDate = new Date();

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
		public static interface ModelDescriptionStringGenerator
		{
			String getModelDescription();
		}

		// public String modelDescription;
		public ModelDescriptionStringGenerator modelDescriptionStringGenerator;
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
			IProject project, ModelDescriptionConfig config, PrintStream out,
			PrintStream err) throws AbortException, IOException
	{
		out.println("Setting generation date to: " + this.generationDate);
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

			Comparator<PDefinition> defAnnotationComporator = new Comparator<PDefinition>()
			{
				@Override
				public int compare(PDefinition d1, PDefinition d2)
				{
					return getDefName(d1).compareTo(getDefName(d2));
				}
			};

			SortedMap<PDefinition, FmuAnnotation> sortedDefAnn = new TreeMap<PDefinition, FmuAnnotation>(defAnnotationComporator);
			sortedDefAnn.putAll(definitionAnnotation);
			for (Entry<PDefinition, FmuAnnotation> link : sortedDefAnn.entrySet())
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
				String scalarVariable = createScalarVariable(err, vr, link.getKey(), link.getValue(), sbLinks);
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

		final String modelDescriptionTemplate = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("modelDescriptionTemplate.xml"));// PluginFolderInclude.readFile(IFmuExport.PLUGIN_ID,

		info.modelDescriptionStringGenerator = new GeneratorInfo.ModelDescriptionStringGenerator()
		{

			@Override
			public String getModelDescription()
			{
				StringBuffer sbSourceFiles = createSourceFileElements(config);

				String modelDescription = modelDescriptionTemplate.replace("<!-- {SCALARVARIABLES} -->", sbScalarVariables.toString());
				modelDescription = modelDescription.replace("<!-- {OUTPUTS} -->", sbOutputs.toString());
				modelDescription = modelDescription.replace("<!-- {LINKS} -->", sbLinks.toString());
				modelDescription = modelDescription.replace("<!-- {SourceFiles} -->", sbSourceFiles.toString());

				modelDescription = modelDescription.replace("{modelName}", project.getName());
				modelDescription = modelDescription.replace("{modelIdentifier}", project.getName());

				try
				{
					Properties prop = new Properties();
					InputStream coeProp = Main.class.getResourceAsStream("/fmu-import-export.properties");
					prop.load(coeProp);
					modelDescription = modelDescription.replace("{overture.fmu.version}", prop.getProperty("version"));
				} catch (Exception e)
				{
				}

				modelDescription = modelDescription.replace("{needsExecutionTool}", config.needsExecutionTool
						+ "");
				modelDescription = modelDescription.replace("{canBeInstantiatedOnlyOncePerProcess}", config.canBeInstantiatedOnlyOncePerProcess
						+ "");

				modelDescription = modelDescription.replace("{description}", "");
				modelDescription = modelDescription.replace("{author}", "");
				modelDescription = modelDescription.replace("{guid}", "{"
						+ config.fmuGUID + "}");

				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
				String date = sdf.format(generationDate);

				modelDescription = modelDescription.replace("{generationDateAndTime}", date);

				return modelDescription;
			}
		};

		info.maxVariableReference = variableReference;
		return info;
	}

	protected StringBuffer createSourceFileElements(
			ModelDescriptionConfig config)
	{
		StringBuffer sbSourceFiles = new StringBuffer();

		for (String source : config.sourceFiles)
		{
			sbSourceFiles.append(String.format("\t\t\t\t<File name=\"%s\" />\n", source));
		}
		return sbSourceFiles;
	}

	private String getDefName(PDefinition def)
	{
		if (def instanceof AValueDefinition)
		{

			// parameter generation
			AValueDefinition vDef = (AValueDefinition) def;

			String name = def.getLocation().getModule() + "."
					+ vDef.getPattern();
			return name;
		} else if (def instanceof AInstanceVariableDefinition)
		{
			String name = def.getLocation().getModule() + "."
					+ def.getName().getFullName();
			return name;
		}
		return def.toString();
	}

	private String createScalarVariable(PrintStream err, int valueReference,
			PDefinition definition, FmuAnnotation annotation,
			StringBuffer sbLinks) throws AbortException
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

			String type = getType(err, vDef.getType(), vDef.getExpression());
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
				String type = getType(err, vDef.getType(), vDef.getExpression());
				return String.format(scalarVariableTemplate, name, valueReference, "output", "discrete", "approx", type);

			} else if (annotation.type.equals("local"))
			{
				AInstanceVariableDefinition vDef = (AInstanceVariableDefinition) definition;
				String type = getType(err, vDef.getType(), null);
				return String.format(scalarVariableTemplate, name, valueReference, "local", "discrete", "calculated", type);

			} else if (annotation.type.equals("input"))
			{

				AInstanceVariableDefinition vDef = (AInstanceVariableDefinition) definition;
				PType rawType = vDef.getType();
				String type = getType(err, rawType, vDef.getExpression());
				return String.format(scalarVariableTemplateInput, name, valueReference, "input", rawType instanceof ARealNumericBasicType ? "continuous"
						: "discrete", type);

			}
		}

		return null;
	}

	private String getType(PrintStream err, PType type, PExp initialExp)
			throws AbortException
	{
		String typeTemplate = null;
		String initial = null;

		if (initialExp instanceof ANewExp)
		{
			if (!((ANewExp) initialExp).getArgs().isEmpty())
			{
				PExp initialArg = ((ANewExp) initialExp).getArgs().get(0);
				if (initialArg instanceof AStringLiteralExp)
				{
					initial = ((AStringLiteralExp) initialArg).getValue().getValue();
				} else
				{
					initial = initialArg.toString();
				}
			}
		}

		if (type instanceof AClassType)
		{
			String name = ((AClassType) type).getName().getName();

			if (name.equals("IntPort"))
			{
				if (initial != null)
				{
					try
					{
						initial = Integer.parseInt(initial) + "";
					} catch (NumberFormatException e)
					{
						try
						{
							initial = (int) Double.parseDouble(initial) + "";
						} catch (NumberFormatException e2)
						{
							String msg = "Unable to decode initial value for IntPort: '"
									+ initial + "'";
							err.println(msg);
							throw new AbortException(msg);
						}
					}
				} else
				{
					initial = "0";
				}
				typeTemplate = scalarVariableIntegerTypeTemplate;
			} else if (name.equals("RealPort"))
			{
				typeTemplate = scalarVariableRealTypeTemplate;
				if (initial != null)
				{
					if (!initial.contains("."))
					{
						initial += ".0";
					}

					try
					{
						Double.parseDouble(initial);
					} catch (NumberFormatException e2)
					{
						String msg = "Unable to decode initial value for RealPort: '"
								+ initial + "'";
						err.println(msg);
						throw new AbortException(msg);
					}
				} else
				{
					initial = "0.0";
				}
			}
			if (name.equals("BoolPort"))
			{
				typeTemplate = scalarVariableBooleanTypeTemplate;
				if (initial == null)
				{
					initial = "false";
				} else
				{
					if (!("true".equals(initial) || "false".equals(initial)))
					{
						String msg = "Unable to decode initial value for BoolPort: '"
								+ initial + "'";
						err.println(msg);
						throw new AbortException(msg);
					}
				}
			}
			if (name.equals("StringPort"))
			{
				typeTemplate = scalarVariableStringTypeTemplate;
				if (initial == null)
				{
					initial = "";
				}
			}
		} else
		{

			// this is branch of is deprecated
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

			} else if (VdmAnnotationProcesser.isStringType(type))
			{
				typeTemplate = scalarVariableStringTypeTemplate;
			}
		}
		String start = initial != null && initialExp != null ? String.format(scalarVariableStartTemplate, initial.replaceAll("\"", "&quot;").replaceAll("\'", "&apos;"))
				: "";
		return String.format(typeTemplate, start);
	}

}
