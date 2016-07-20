package org.overture.fmi.ide.fmuexport.commands;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.overture.ast.analysis.AnalysisException;
import org.overture.ast.analysis.DepthFirstAnalysisAdaptor;
import org.overture.ast.definitions.AInstanceVariableDefinition;
import org.overture.ast.definitions.ASystemClassDefinition;
import org.overture.ast.definitions.AThreadDefinition;
import org.overture.ast.definitions.AValueDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.expressions.AVariableExp;
import org.overture.ast.statements.APeriodicStm;
import org.overture.ast.statements.AStartStm;
import org.overture.ast.types.ABooleanBasicType;
import org.overture.ast.types.AClassType;
import org.overture.ast.types.AOptionalType;
import org.overture.ast.types.ARealNumericBasicType;
import org.overture.ast.types.PType;
import org.overture.ast.types.SNumericBasicType;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.fmi.ide.fmuexport.commands.ModelDescriptionGenerator.GeneratorInfo;
import org.overture.fmi.ide.fmuexport.commands.ModelDescriptionGenerator.ScalarInfo;
import org.overture.ide.core.ast.NotAllowedException;
import org.overture.ide.core.resources.IVdmProject;
import org.overture.ide.plugins.cgen.generator.CGenerator;
import org.overture.ide.ui.utility.PluginFolderInclude;

public class ExportSourceCodeFmuHandler extends ExportFmuHandler
{

	private static final String SET_FIELD = "SET_FIELD(HardwareInterface, HardwareInterface, g_%s_hwi, %s, %s(fmiBuffer.%s[%s]));";
	private static final String SYNC_INPUT_TO_MODEL = "void syncInputsToModel(){\n\t%s\n}";
	private static final String SYNC_OUTPUT_TO_BUFFER = "void syncOutputsToBuffers(){\n\t%s\n}";

	@Override
	protected String getTitle()
	{
		return "Source Code FMU";
	}

	@Override
	protected void copyFmuResources(GeneratorInfo info, IFolder thisFmu,
			String name, IVdmProject project, ASystemClassDefinition system)
			throws CoreException, IOException, AnalysisException,
			NotAllowedException
	{
		final String systemName = system.getName().getName();

		final IFolder sourcesFolder = thisFmu.getFolder("sources");

		if (!sourcesFolder.exists())
		{
			sourcesFolder.create(IResource.NONE, true, null);
		}

		CGenerator generator = new CGenerator(project);

		generator.generate(sourcesFolder.getLocation().toFile());
		sourcesFolder.refreshLocal(IResource.DEPTH_INFINITE, null);

		final List<PeriodicThreadDef> periodicDefs = extractPeriodicDefs(project);

		String periodicDefinition = createPeriodicDefinitionString(periodicDefs);

		StringBuffer sb = generateIOCacheSyncMethods(info, periodicDefinition, systemName);

		// copy FMU files
		copy(sourcesFolder, "Fmu.cpp", "includes/c-templates/Fmu.cpp");
		copy(sourcesFolder, "FmuIO.c", "includes/c-templates/FmuIO.c");

		IFile fileFmuh = createNewEmptyFile(sourcesFolder, "Fmu.h");
		String contentFmuh = PluginFolderInclude.readFile(IFmuExport.PLUGIN_ID, "includes/c-templates/Fmu.h");
		contentFmuh = contentFmuh.replace("//#define BOOL_COUNT", "#define BOOL_COUNT "
				+ info.maxVariableReference);
		contentFmuh = contentFmuh.replace("//#define REAL_COUNT", "#define REAL_COUNT "
				+ info.maxVariableReference);
		contentFmuh = contentFmuh.replace("//#define INT_COUNT", "#define INT_COUNT "
				+ info.maxVariableReference);

		byte[] bytes = contentFmuh.getBytes("UTF-8");
		ByteArrayInputStream source = new ByteArrayInputStream(bytes);
		fileFmuh.create(source, IResource.NONE, null);

		// copy FMI headers to support CMakeLists
		final IFolder fmiFolder = sourcesFolder.getFolder("fmi");

		if (!fmiFolder.exists())
		{
			fmiFolder.create(IResource.NONE, true, null);
		}
		copy(fmiFolder, "fmi2Functions.h", "includes/c-templates/fmi/fmi2Functions.h");
		copy(fmiFolder, "fmi2FunctionTypes.h", "includes/c-templates/fmi/fmi2FunctionTypes.h");
		copy(fmiFolder, "fmi2TypesPlatform.h", "includes/c-templates/fmi/fmi2TypesPlatform.h");

		// copy FmuModel.cpp
		IFile file = createNewEmptyFile(sourcesFolder, "FmuModel.cpp");
		String content = PluginFolderInclude.readFile(IFmuExport.PLUGIN_ID, "includes/c-templates/FmuModel.cpp");

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
		file.create(source, IResource.NONE, null);

		// copy CMakeLists
		file = createNewEmptyFile(sourcesFolder, "CMakeLists.txt");
		content = PluginFolderInclude.readFile(IFmuExport.PLUGIN_ID, "includes/c-templates/CMakeLists.txt");

		content = content.replace("##PROJECT_NAME##", project.getName());

		bytes = content.getBytes("UTF-8");
		source = new ByteArrayInputStream(bytes);
		file.create(source, IResource.NONE, null);

		createNewEmptyFile(sourcesFolder, "defines.def").create(new ByteArrayInputStream("CUSTOM_IO".getBytes("UTF-8")), IResource.NONE, null);
		createNewEmptyFile(sourcesFolder, "includes.txt").create(new ByteArrayInputStream("fmi\nvdmlib".getBytes("UTF-8")), IResource.NONE, null);

		// Clean up after bad vdm2c lib generation
		IFolder vdmlibFolder = sourcesFolder.getFolder("nativelib");
		if (vdmlibFolder.exists())
		{
			for (String n : new String[] { "tests", "classes", "records" })
			{
				IFolder folder = vdmlibFolder.getFolder(n);
				if (folder.exists())
				{
					folder.delete(true, null);
				}
			}
			IFolder tmp = sourcesFolder.getFolder("vdmlib");
			vdmlibFolder.move(tmp.getFullPath(), true, false, null);
		}
	}

	private CharSequence getSystemInitFunction(IVdmProject project,
			String systemName) throws NotAllowedException
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

	private CharSequence getSystemShutdownFunction(IVdmProject project,
			String systemName) throws NotAllowedException
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

	private String getJoinClassNames(IVdmProject project, INameFormater formater)
			throws NotAllowedException
	{
		List<String> includes = new Vector<>();
		for (SClassDefinition def : project.getModel().getClassList())
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

			if (type instanceof ARealNumericBasicType)
			{
				bufferName = "realBuffer";
				newValueName = "newReal";
				valueId = "doubleVal";
			} else if (type instanceof ABooleanBasicType)
			{
				bufferName = "booleanBuffer";
				newValueName = "newBool";
				valueId = "boolVal";
			} else if (type instanceof SNumericBasicType)
			{
				bufferName = "intBuffer";
				newValueName = "newInt";
				valueId = "intVal";
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
				command = String.format(SET_FIELD, systemName, variableName, newValueName, bufferName, entry.getValue().index);
				inputCommands.add(command);

			} else if ("output".equals(svType))
			{
				// fmiBuffer.booleanBuffer[VALVE_ID]=GET_FIELD(HardwareInterface,HardwareInterface,g_System_hwi,valveState)->value.boolVal;
				final String GET_FIELD = "fmiBuffer.%s[%s]=GET_FIELD(HardwareInterface,HardwareInterface,g_%s_hwi,%s)->value.%s;";
				command = String.format(GET_FIELD, bufferName, entry.getValue().index, systemName, variableName, valueId);
				outputCommands.add(command);

			} else if ("parameter".equals(svType))
			{
				// replaceTvp(&g_HardwareInterface_minlevel, newReal(fmiBuffer.realBuffer[MIN_LEVEL_ID]));
				final String PARAMETER = "replaceTvp(&g_HardwareInterface_%s, %s(fmiBuffer.%s[%s]));";
				command = String.format(PARAMETER, variableName, newValueName, bufferName, entry.getValue().index);
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

	public List<PeriodicThreadDef> extractPeriodicDefs(IVdmProject project)
			throws NotAllowedException, AnalysisException
	{
		final List<PeriodicThreadDef> periodicDefs = new Vector<>();

		for (SClassDefinition cdef : project.getModel().getClassList())
		{
			if ("World".equals(cdef.getName().getName()))
			{
				for (PDefinition def : cdef.getDefinitions())
				{
					if ("run".equals(def.getName().getName()))
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

	void copy(IFolder sourcesFolder, String fileName, String contentUrl)
			throws CoreException, UnsupportedEncodingException, IOException
	{
		IFile file = createNewEmptyFile(sourcesFolder, fileName);
		byte[] bytes = PluginFolderInclude.readFile(IFmuExport.PLUGIN_ID, contentUrl).getBytes("UTF-8");
		ByteArrayInputStream source = new ByteArrayInputStream(bytes);
		file.create(source, IResource.NONE, null);
	}

	public IFile createNewEmptyFile(final IFolder sourcesFolder, String name)
			throws CoreException
	{
		IFile periodicThreadsSource = sourcesFolder.getFile(name);

		if (periodicThreadsSource.exists())
		{
			periodicThreadsSource.delete(true, null);
		}
		return periodicThreadsSource;
	}

}
