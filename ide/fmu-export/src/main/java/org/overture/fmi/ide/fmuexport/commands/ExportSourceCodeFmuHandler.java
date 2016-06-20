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

	private static final String SET_FIELD = "SET_FIELD(HardwareInterface, HardwareInterface, g_System_hwi, %s, %s(fmiBuffer.%s[%s]));";
	private static final String SYNC_INPUT_TO_MODEL = "void syncInputsToModel(){\n\t%s\n}";
	private static final String SYNC_OUTPUT_TO_BUFFER = "void syncOutputsToBuffers(){\n\t%s\n}";

	@Override
	protected String getTitle()
	{
		return "Source Code FMU";
	}

	@Override
	protected void copyFmuResources(GeneratorInfo info, IFolder thisFmu,
			String name, IVdmProject project) throws CoreException,
			IOException, AnalysisException, NotAllowedException
	{
		final IFolder sourcesFolder = thisFmu.getFolder("sources");

		if (!sourcesFolder.exists())
		{
			sourcesFolder.create(IResource.NONE, true, null);
		}

		CGenerator generator = new CGenerator(project);

		generator.generate(sourcesFolder.getLocation().toFile());
		sourcesFolder.refreshLocal(IResource.DEPTH_INFINITE, null);

		final List<String> periodicDefs = extractPeriodicDefs(project);

		// patch template
		/*
		 * struct PeriodicThreadStatus threads[] = { ... };
		 */

		String periodicDefinition = createPeriodicDefinitionString(periodicDefs);
		// byte[] bytes = sb.toString().getBytes("UTF-8");
		// InputStream source = new ByteArrayInputStream(bytes);
		// periodicThreadsSource.create(source, IResource.NONE, null);

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
				command = String.format(SET_FIELD, variableName, newValueName, bufferName, entry.getValue().index);
				inputCommands.add(command);

			} else if ("output".equals(svType))
			{
				// fmiBuffer.booleanBuffer[VALVE_ID]=GET_FIELD(HardwareInterface,HardwareInterface,g_System_hwi,valveState)->value.boolVal;
				final String GET_FIELD = "fmiBuffer.%s[%s]=GET_FIELD(HardwareInterface,HardwareInterface,g_System_hwi,%s)->value.%s;";
				command = String.format(GET_FIELD, bufferName, entry.getValue().index, variableName, valueId);
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

		//copy FMU files
		copy(sourcesFolder, "Fmu.cpp", "includes/c-templates/Fmu.cpp");
		copy(sourcesFolder, "Fmu.h", "includes/c-templates/Fmu.h");
		copy(sourcesFolder, "FmuIO.c", "includes/c-templates/FmuIO.c");

		//copy FMI headers to support CMakeLists
		final IFolder fmiFolder = sourcesFolder.getFolder("fmi");

		if (!fmiFolder.exists())
		{
			fmiFolder.create(IResource.NONE, true, null);
		}
		copy(fmiFolder, "fmi2Functions.h", "includes/c-templates/fmi/fmi2Functions.h");
		copy(fmiFolder, "fmi2FunctionTypes.h", "includes/c-templates/fmi/fmi2FunctionTypes.h");
		copy(fmiFolder, "fmi2TypesPlatform.h", "includes/c-templates/fmi/fmi2TypesPlatform.h");
		
		
		//copy FmuModel.cpp
		IFile file = createNewEmptyFile(sourcesFolder, "FmuModel.cpp");
		String content = PluginFolderInclude.readFile(IFmuExport.PLUGIN_ID, "includes/c-templates/FmuModel.cpp");

		content = content.replace("//#GENERATED_INSERT", sb.toString());

		byte[] bytes = content.getBytes("UTF-8");
		ByteArrayInputStream source = new ByteArrayInputStream(bytes);
		file.create(source, IResource.NONE, null);

		//copy CMakeLists
		file = createNewEmptyFile(sourcesFolder, "CMakeLists.txt");
		content = PluginFolderInclude.readFile(IFmuExport.PLUGIN_ID, "includes/c-templates/CMakeLists.txt");

		content = content.replace("##PROJECT_NAME##", project.getName());

		bytes = content.getBytes("UTF-8");
		source = new ByteArrayInputStream(bytes);
		file.create(source, IResource.NONE, null);

	}

	private String createPeriodicDefinitionString(List<String> periodicDefs)
	{
		StringBuffer periodicStringDefs = new StringBuffer();

		for (Iterator<String> itr = periodicDefs.iterator(); itr.hasNext();)
		{
			String def = (String) itr.next();
			periodicStringDefs.append(def);
			if (itr.hasNext())
			{
				periodicStringDefs.append(",\n");
			}

		}

		StringBuffer sb = new StringBuffer();
		sb.append("#define PERIODIC_GENERATED\n");
		sb.append("#define PERIODIC_GENERATED_COUNT ");
		sb.append(periodicDefs.size());
		sb.append("\n");
		sb.append("\n");
		sb.append(String.format("struct PeriodicThreadStatus threads[] ={\n%s\n};", periodicStringDefs.toString()));
		return sb.toString();
	}

	public List<String> extractPeriodicDefs(IVdmProject project)
			throws NotAllowedException, AnalysisException
	{
		final List<String> periodicDefs = new Vector<>();

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
										// CALL_FUNC(World, World, world, CLASS_World__Z3runEV);
										String className = varExp.getVardef().getClassDefinition().getName().getName();
										// g_System_hwi
										String objectName = String.format("g_%s_%s", className, varExp.getName().getName());

										PType type = varExp.getType();
										if (type instanceof AOptionalType)
										{
											type = ((AOptionalType) type).getType();
										}

										if (type instanceof AClassType)
										{
											AClassType cType = (AClassType) type;

											for (PDefinition pDef : cType.getClassdef().getDefinitions())
											{
												if (pDef instanceof AThreadDefinition)
												{
													AThreadDefinition tDef = (AThreadDefinition) pDef;

													if (tDef.getStatement() instanceof APeriodicStm)
													{
														APeriodicStm periodicStm = (APeriodicStm) tDef.getStatement();

														String period = ""
																+ periodicStm.getArgs().get(0);

														String name = periodicStm.getOpname().getName();
														// _Z4loopEV
														String mangledName = String.format("_Z%d%sEV", name.length(), name);
														// CLASS_Controller__Z4loopEV
														String callDef = String.format("CLASS_%s_%s", className, mangledName);

														String periodicDef = String.format("{ %s, \"%s\", \"%s\", 0 }", period, objectName, callDef);
														periodicDefs.add(periodicDef);
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
