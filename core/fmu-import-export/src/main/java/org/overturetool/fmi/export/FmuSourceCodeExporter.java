package org.overturetool.fmi.export;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.overture.ast.analysis.AnalysisException;
import org.overture.ast.analysis.DepthFirstAnalysisAdaptor;
import org.overture.ast.definitions.AInstanceVariableDefinition;
import org.overture.ast.definitions.ASystemClassDefinition;
import org.overture.ast.definitions.AThreadDefinition;
import org.overture.ast.definitions.AValueDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.expressions.AVariableExp;
import org.overture.ast.patterns.AIdentifierPattern;
import org.overture.ast.patterns.PPattern;
import org.overture.ast.statements.APeriodicStm;
import org.overture.ast.statements.AStartStm;
import org.overture.ast.types.AClassType;
import org.overture.ast.types.AOptionalType;
import org.overture.ast.types.PType;
import org.overturetool.fmi.IProject;
import org.overturetool.fmi.export.ModelDescriptionGenerator.GeneratorInfo;
import org.overturetool.fmi.export.ModelDescriptionGenerator.ScalarInfo;

public class FmuSourceCodeExporter extends FmuExporter
{
	private static final String SYNC_INPUT_TO_MODEL = "void syncInputsToModel(){\n\t%s\n}";
	private static final String SYNC_OUTPUT_TO_BUFFER = "void syncOutputsToBuffers(){\n\t%s\n}";

	static final String GET_PORT = "TVP p = GET_FIELD(HardwareInterface,HardwareInterface,g_%s_hwi,%s);";
	static final String GET_PORT_VALUE = "TVP v = GET_FIELD(%s,%s,p,value);";
	static final String SET_PORT_VALUE = "SET_FIELD(%s,%s,p,value,newValue);";
	static final String SET_PORT_WITH_VALUE = "{\n\t\tTVP newValue = %s(fmiBuffer.%s[%s]);\n\t\t%s\n\t\t%s\n\t\tvdmFree(newValue);vdmFree(p);\n\t}";
	static final String PARAMETER = "{\n\t\tTVP newValue = %s(fmiBuffer.%s[%s]);\n\t\t"
			+ "SET_FIELD(%s,%s,g_HardwareInterface_%s,value,newValue);\n\t\t"
			+ "vdmFree(newValue);\n\t}";

	protected ModelDescriptionConfig getModelDescriptionConfig(IProject project)
	{
		ModelDescriptionConfig config = new ModelDescriptionConfig();
		config.canBeInstantiatedOnlyOncePerProcess = true;
		config.needsExecutionTool = false;

		return config;
	}

	protected String getExportType()
	{
		return "c-code";
	}

	@Override
	protected void copyFmuResources(GeneratorInfo info, String name,
			IProject project,ModelDescriptionConfig modelDescriptionConfig, ASystemClassDefinition system, PrintStream out,
			PrintStream err) throws IOException, AnalysisException
	{
		final String systemName = system.getName().getName();
		final String sources = "sources";
		final String resourcesFolder = "resources";
		List<File> emittedFiles, emittedFilesTmp;
		LinkedList<File> resourceFiles;

		CGenerator generator = new CGenerator(project);

		//Add just the generated files to the list of files first.
		emittedFiles = new LinkedList<>(generator.generate(new File(project.getTempFolder(), sources), out, err));
		emittedFilesTmp = new LinkedList<>();

		//Filter out non-source code files.		
		for(int i = 0; i < emittedFiles.size(); i++)
		{
			if(emittedFiles.get(i).getName().endsWith(".c") ||
					emittedFiles.get(i).getName().endsWith(".c") ||
					emittedFiles.get(i).getName().endsWith(".cpp") ||
					emittedFiles.get(i).getName().endsWith(".h"))
			{
				emittedFilesTmp.add(emittedFiles.get(i));
			}
		}

		if( ! emittedFilesTmp.isEmpty())
			emittedFiles = emittedFilesTmp;


		final List<PeriodicThreadDef> periodicDefs = extractPeriodicDefs(project);

		String periodicDefinition = createPeriodicDefinitionString(periodicDefs);

		StringBuffer sb = generateIOCacheSyncMethods(info, periodicDefinition, systemName);


		//EMIT GUID HEADER FILE HERE.

		// copy FMU files
		copySource(project, sources + "/Fmu.c", "/c-templates/Fmu.c");
		emittedFiles.add(new File("Fmu.c"));
		copySource(project, sources + "/FmuIO.c", "/c-templates/FmuIO.c");
		emittedFiles.add(new File("FmuIO.c"));

		String contentFmuh = IOUtils.toString(this.getClass().getResourceAsStream("/c-templates/Fmu.h"));
		contentFmuh = contentFmuh.replace("//#define BOOL_COUNT", "#define BOOL_COUNT "
				+ info.maxVariableReference);
		contentFmuh = contentFmuh.replace("//#define REAL_COUNT", "#define REAL_COUNT "
				+ info.maxVariableReference);
		contentFmuh = contentFmuh.replace("//#define INT_COUNT", "#define INT_COUNT "
				+ info.maxVariableReference);

		byte[] bytes = contentFmuh.getBytes("UTF-8");
		ByteArrayInputStream source = new ByteArrayInputStream(bytes);
		project.createProjectTempRelativeFile(sources + "/Fmu.h", source);

		// copy FMI headers to support CMakeLists
		copySource(project, sources + "/fmi/fmi2Functions.h", "/c-templates/fmi/fmi2Functions.h");
		emittedFiles.add(new File("fmi/fmi2Functions.h"));
		copySource(project, sources + "/fmi/fmi2FunctionTypes.h", "/c-templates/fmi/fmi2FunctionTypes.h");
		emittedFiles.add(new File("fmi/fmi2FunctionTypes.h"));
		copySource(project, sources + "/fmi/fmi2TypesPlatform.h", "/c-templates/fmi/fmi2TypesPlatform.h");
		emittedFiles.add(new File("fmi/fmi2TypesPlatform.h"));

		// copy FmuModel.c
		String content = IOUtils.toString(this.getClass().getResourceAsStream("/c-templates/FmuModel.c"));

		content = content.replace("//#GENERATED_DEFINES", "#define PERIODIC_GENERATED\n");
		content = content.replace("//#GENERATED_INSERT", sb.toString());
		content = content.replace("//#GENERATED_MODEL_INCLUDE", getJoinClassNames(project, new INameFormater()
		{

			@Override
			public String format(String className)
			{
				return "#include \"" + className + ".h\"";
			}
		}));

		content = content.replace("//#GENERATED_SYSTEM_INIT", getSystemInitFunction(project, systemName));
		content = content.replace("//#GENERATED_SYSTEM_SHUTDOWN", getSystemShutdownFunction(project, systemName));

		bytes = content.getBytes("UTF-8");
		source = new ByteArrayInputStream(bytes);
		project.createProjectTempRelativeFile(sources + "/FmuModel.c", source);
		emittedFiles.add(new File("FmuModel.c"));

		// copy CMakeLists
		content = IOUtils.toString(this.getClass().getResourceAsStream("/c-templates/CMakeLists.txt"));

		content = content.replace("##PROJECT_NAME##", project.getName());

		bytes = content.getBytes("UTF-8");
		source = new ByteArrayInputStream(bytes);
		project.createProjectTempRelativeFile(sources + "/CMakeLists.txt", source);

		project.createProjectTempRelativeFile(sources + "/defines.def", new ByteArrayInputStream("CUSTOM_IO".getBytes("UTF-8")));
		project.createProjectTempRelativeFile(sources + "/includes.txt", new ByteArrayInputStream("fmi\nvdmlib".getBytes("UTF-8")));

		//Populate list of source files in modelDescription.xml file.
		for(int i = 0; i < emittedFiles.size(); i++)
		{
			modelDescriptionConfig.sourceFiles.add(
					emittedFiles.get(i).toString());
		}

		copyResourceFiles(project, resourcesFolder, new String[]{"csv", "vdmrt"});
	}

	private void copySource(IProject project, String path, String sourcePath)
			throws IOException
	{
		InputStream is = this.getClass().getResourceAsStream(sourcePath);
		if (is == null)
		{
			throw new IOException("File not found in class path: " + sourcePath);
		}
		project.createProjectTempRelativeFile(path, is);

	}

	private CharSequence getSystemInitFunction(IProject project,
			String systemName)
	{
		StringBuilder sb = new StringBuilder();

		sb.append(getJoinClassNames(project, new INameFormater()
		{

			@Override
			public String format(String className)
			{
				return "\t" + className + "_const_init();";
			}
		}));

		sb.append("\n");
		sb.append("\n");

		sb.append(getJoinClassNames(project, new INameFormater()
		{

			@Override
			public String format(String className)
			{
				return "\t" + className + "_static_init();";
			}
		}));

		sb.append("\n");
		sb.append("\n");

		sb.append("\tsys = "
				+ String.format("_Z%d%sEV(NULL);\n", systemName.length(), systemName));

		return String.format("void systemInit()\n{\n%s\n}\n", sb.toString());
	}

	private CharSequence getSystemShutdownFunction(IProject project,
			String systemName)
	{
		StringBuilder sb = new StringBuilder();

		sb.append(getJoinClassNames(project, new INameFormater()
		{

			@Override
			public String format(String className)
			{
				return "\t" + className + "_static_shutdown();";
			}
		}));

		sb.append("\n");
		sb.append("\n");

		sb.append(getJoinClassNames(project, new INameFormater()
		{

			@Override
			public String format(String className)
			{
				return "\t" + className + "_const_shutdown();";
			}
		}));

		sb.append("\n");
		sb.append("\n");

		sb.append("\tvdmFree(sys);\n");

		return String.format("void systemDeInit()\n{\n%s\n}\n", sb.toString());
	}

	static interface INameFormater
	{
		String format(String className);
	}

	private String getJoinClassNames(IProject project, INameFormater formater)

	{
		List<String> includes = new Vector<>();
		for (SClassDefinition def : project.getClasses())
		{
			includes.add(formater.format(def.getName().getName()));
		}

		return StringUtils.join(includes, "\n");
	}

	public StringBuffer generateIOCacheSyncMethods(GeneratorInfo info,
			String periodicDefinition, Object systemName)
	{
		List<String> inputCommands = new Vector<>();
		List<String> outputCommands = new Vector<>();

		for (Entry<PDefinition, ScalarInfo> entry : info.context.entrySet())
		{
			PType type = entry.getKey().getType();
			if (type instanceof AOptionalType)
			{
				type = ((AOptionalType) type).getType();
			}

			String bufferName = "";
			String newValueName = "";
			String valueId = "";
			String className = null;
			if (type instanceof AClassType)
			{
				className = ((AClassType) type).getName().getName();

				if ("RealPort".equals(className))
				{
					bufferName = "realBuffer";
					newValueName = "newReal";
					valueId = "doubleVal";
				} else if ("BoolPort".equals(className))
				{
					bufferName = "booleanBuffer";
					newValueName = "newBool";
					valueId = "boolVal";
				} else if ("IntPort".equals(className))
				{
					bufferName = "intBuffer";
					newValueName = "newInt";
					valueId = "intVal";
				}
			}

			String svType = entry.getValue().annotation.type;
			String command = null;
			String variableName = null;

			if (entry.getKey() instanceof AInstanceVariableDefinition)
			{
				variableName = entry.getKey().getName().getName();
			} else if (entry.getKey() instanceof AValueDefinition)
			{
				variableName = ((AValueDefinition) entry.getKey()).getPattern().toString();
			}

			if ("input".equals(svType))
			{

				command = String.format(SET_PORT_WITH_VALUE, newValueName, bufferName, entry.getValue().index, String.format(GET_PORT, systemName, variableName), String.format(SET_PORT_VALUE, className, className, variableName), newValueName);
				inputCommands.add(command);

			} else if ("output".equals(svType))
			{
				// fmiBuffer.booleanBuffer[VALVE_ID]=GET_FIELD(HardwareInterface,HardwareInterface,g_System_hwi,valveState)->value.boolVal;
				final String GET_FIELD = "{\n\t\t%s\n\t\t%s\n\t\tfmiBuffer.%s[%s]=v->value.%s;\n\t\tvdmFree(v);vdmFree(p);\n\t}";// "fmiBuffer.%s[%s]=GET_FIELD(HardwareInterface,HardwareInterface,g_%s_hwi,%s)->value.%s;";
				command = String.format(GET_FIELD, String.format(GET_PORT, systemName, variableName), String.format(GET_PORT_VALUE, className, className), bufferName, entry.getValue().index, valueId);
				outputCommands.add(command);

			} else if ("parameter".equals(svType))
			{

				/*
				 * TVP newValue = newReal(fmiBuffer.realBuffer[0]);
				 * SET_FIELD(RealPort,RealPort,g_HardwareInterface_maxlevel,value,newValue); vdmFree(newValue);
				 */

				// replaceTvp(&g_HardwareInterface_minlevel, newReal(fmiBuffer.realBuffer[MIN_LEVEL_ID]));

				// final String PARAMETER = "replaceTvp(&g_HardwareInterface_%s, %s(fmiBuffer.%s[%s]));";
				command = String.format(PARAMETER, newValueName, bufferName, entry.getValue().index, className, className, variableName);
				inputCommands.add(command);
			}
		}

		StringBuffer sb = new StringBuffer();
		sb.append("\n");
		sb.append(String.format(SYNC_INPUT_TO_MODEL, StringUtils.join(inputCommands, "\n\t")));
		sb.append("\n");
		sb.append(String.format(SYNC_OUTPUT_TO_BUFFER, StringUtils.join(outputCommands, "\n\t")));
		sb.append("\n");
		sb.append(periodicDefinition);
		sb.append("\n");
		return sb;
	}

	private String createPeriodicDefinitionString(
			List<PeriodicThreadDef> periodicDefs)
	{
		StringBuffer periodicTasks = new StringBuffer();
		StringBuffer periodicStringDefs = new StringBuffer();

		for (Iterator<PeriodicThreadDef> itr = periodicDefs.iterator(); itr.hasNext();)
		{
			PeriodicThreadDef def = itr.next();
			periodicStringDefs.append(def.getInstance());
			periodicTasks.append(def.getPeriodicTaskCallFunctionDefinition());
			if (itr.hasNext())
			{
				periodicStringDefs.append(",\n");
				periodicTasks.append("\n");
			}

		}

		StringBuffer sb = new StringBuffer();
		sb.append("#define PERIODIC_GENERATED_COUNT ");
		sb.append(periodicDefs.size());
		sb.append("\n");
		sb.append("\n");
		sb.append(periodicTasks.toString());
		sb.append("\n");
		sb.append("\n");
		sb.append(String.format("struct PeriodicThreadStatus threads[] ={\n%s\n};", periodicStringDefs.toString()));
		return sb.toString();
	}

	static class PeriodicThreadDef
	{
		final static String PeriodicTaskName = "periodic_task%s_%s";
		final static String PeriodicTaskFunctionTemplate = "void %s()\n{\n\tCALL_FUNC(%s, %s, %s, %s);\n\t%s\n}\n";
		final static String PeriodicTaskFunctionLogCallTemplate = "g_fmiCallbackFunctions->logger((void*) 1, g_fmiInstanceName, fmi2OK, \"logAll\", \"called %s\\n\");";
		final static String PeriodicTaskCallTemplate = "&%s";
		// String periodicTaskFunction;
		// String perioducTaskCall;

		String objectName;
		String className;
		String callName;
		String period;
		String objectTypeName;

		String getGlobalObjectName()
		{
			return String.format("g_%s_%s", className, objectName);
		}

		String getMangledCallName()
		{
			// _Z4loopEV
			return String.format("_Z%d%sEV", callName.length(), callName);
		}

		String getCallName()
		{
			// CLASS_Controller__Z4loopEV
			return String.format("CLASS_%s_%s", objectTypeName, getMangledCallName());
		}

		String getPeriodicTaskCallName()
		{
			return String.format(PeriodicTaskCallTemplate, getPeriodicTaskName());
		}

		String getPeriodicTaskName()
		{
			return String.format(PeriodicTaskName, getGlobalObjectName(), getMangledCallName());
		}

		String getPeriodicTaskCallFunctionDefinition()
		{
			return String.format(PeriodicTaskFunctionTemplate, getPeriodicTaskName(), objectTypeName, objectTypeName, getGlobalObjectName(), getCallName(), String.format(PeriodicTaskFunctionLogCallTemplate, getPeriodicTaskCallName()));
		}

		String getInstance()
		{
			return String.format("{ %s, %s, 0 }", period, getPeriodicTaskCallName());
		}
	}

	public List<PeriodicThreadDef> extractPeriodicDefs(IProject project)
			throws AnalysisException
	{
		final List<PeriodicThreadDef> periodicDefs = new Vector<>();

		for (SClassDefinition cdef : project.getClasses())
		{
			if ("World".equals(cdef.getName().getName()))
			{
				for (PDefinition def : cdef.getDefinitions())
				{
					//Store name here based on what kind of definition it is, since values allow patterns.
					PPattern pat;
					String defName = null;

					if(def instanceof AValueDefinition)
					{
						pat = ((AValueDefinition)def).getPattern();

						if(pat instanceof AIdentifierPattern)
						{
							defName = ((AIdentifierPattern) pat).getName().getName();
						}
						else
						{
							//TODO:
						}
					}
					else
						defName = def.getName().getName();


					if ("run".equals(defName))
					{
						def.apply(new DepthFirstAnalysisAdaptor()
						{
							@Override
							public void caseAStartStm(AStartStm node)
									throws AnalysisException
							{
								if (node.getObj() instanceof AVariableExp)
								{
									AVariableExp varExp = (AVariableExp) node.getObj();
									if (varExp.getVardef().getAccess().getStatic() != null)
									{
										PeriodicThreadDef ptDef = new PeriodicThreadDef();

										ptDef.className = varExp.getVardef().getClassDefinition().getName().getName();
										ptDef.objectName = varExp.getName().getName();

										PType type = varExp.getType();
										if (type instanceof AOptionalType)
										{
											type = ((AOptionalType) type).getType();
										}

										if (type instanceof AClassType)
										{
											AClassType cType = (AClassType) type;
											ptDef.objectTypeName = cType.getName().getName();

											for (PDefinition pDef : cType.getClassdef().getDefinitions())
											{
												if (pDef instanceof AThreadDefinition)
												{
													AThreadDefinition tDef = (AThreadDefinition) pDef;

													if (tDef.getStatement() instanceof APeriodicStm)
													{
														APeriodicStm periodicStm = (APeriodicStm) tDef.getStatement();

														ptDef.period = ""
																+ periodicStm.getArgs().get(0);

														ptDef.callName = periodicStm.getOpname().getName();
														periodicDefs.add(ptDef);
													}
												}
											}

										}
									}
								}
							}
						});
					}
				}
			}
		}
		return periodicDefs;
	}

}
