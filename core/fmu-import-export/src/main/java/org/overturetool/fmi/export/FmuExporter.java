package org.overturetool.fmi.export;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.intocps.java.fmi.shm.SharedMemory;
import org.overture.ast.analysis.AnalysisException;
import org.overture.ast.definitions.ASystemClassDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.util.definitions.ClassList;
import org.overture.fmi.annotation.FmuAnnotation;
import org.overturetool.fmi.AbortException;
import org.overturetool.fmi.IProject;
import org.overturetool.fmi.IProject.IJob;
import org.overturetool.fmi.export.ModelDescriptionGenerator.GeneratorInfo;
import org.overturetool.fmi.util.FolderCompressor;
import org.overturetool.fmi.util.VdmAnnotationProcesser;

public class FmuExporter
{

	public File exportFmu(IProject project, String title, PrintStream out,
			PrintStream err) throws AbortException
	{
		out.println("\n---------------------------------------");
		out.println("|             " + title + "             |");
		out.println("---------------------------------------");
		out.println("Starting FMU export for project: '" + project.getName()
				+ "'");

		if (project.typeCheck())
		{
			try
			{
				Map<PDefinition, FmuAnnotation> definitionAnnotation = new VdmAnnotationProcesser().collectAnnotatedDefinitions(project, out, err);

				ASystemClassDefinition system = null;
				for (SClassDefinition cDef : project.getClasses())
				{
					if (cDef instanceof ASystemClassDefinition)
					{
						system = (ASystemClassDefinition) cDef;
						out.println("Found system class: '"
								+ cDef.getName().getName() + "'");
					}
				}

				Map<String, FmuAnnotation> exportNames = new HashMap<>();
				boolean hasDublications = false;
				for (Entry<PDefinition, FmuAnnotation> entry : definitionAnnotation.entrySet())
				{
					FmuAnnotation value = entry.getValue();
					if (exportNames.containsKey(value.name))
					{
						final String export = "'%s' at line %s with type '%s'";
						FmuAnnotation original = exportNames.get(value.name);
						err.print("Dublicate export name: "
								+ String.format(export, value.name, value.tree.getLine(), value.type)
								+ " dublicates: "
								+ String.format(export, original.name, original.tree.getLine(), original.type));
						hasDublications = true;
					}
					exportNames.put(value.name, value);
				}

				if (hasDublications)
				{
					return null;
				}
				ClassList classList = new ClassList();
				classList.addAll(project.getClasses());
				ModelDescriptionGenerator generator = new ModelDescriptionGenerator(classList, system);

				GeneratorInfo info = generator.generate(definitionAnnotation, project, out, err);

				if (project.isOutputDebugEnabled())
				{
					out.println("\n########################\n Model Description: \n");
					out.println(info.modelDescription);
				}

				project.createProjectTempRelativeFile("modelDescription.xml", new ByteArrayInputStream(info.modelDescription.getBytes("UTF-8")));

				copyFmuResources(info, project.getName(), project, system, out, err);
				final File fmuArchieveName = new File(project.getOutputFolder(), project.getName()
						+ ".fmu");

				project.scheduleJob(new IJob()
				{

					@Override
					public void run()
					{
						File fmuFolderPath;
						try
						{
							fmuFolderPath = project.getTempFolder();
							try
							{
								FolderCompressor.compress(fmuFolderPath, fmuArchieveName);
								project.cleanUp();
							} catch (IOException e)
							{
								project.log(e);
							}
						} catch (IOException e1)
						{
							project.log(e1);
						}

					}
				});

				return fmuArchieveName;

			} catch (IOException e)
			{
				project.log(e);
			} catch (AnalysisException e)
			{
				project.log(e);
			}
		}
		return null;
	}

	protected void copyFmuResources(GeneratorInfo info, String name,
			IProject project, ASystemClassDefinition system, PrintStream out,
			PrintStream err) throws IOException, AnalysisException
	{
		final String resourcesFolder = "resources";

		InputStream is = null;
		final String interpreterJarName = "fmi-interpreter-jar-with-dependencies.jar";

		is = FmuExporter.class.getClassLoader().getResourceAsStream("jars/"
				+ interpreterJarName);

		project.createProjectTempRelativeFile(resourcesFolder + "/"
				+ interpreterJarName, is);

		StringBuffer sb = new StringBuffer();
		sb.append("false\n");
		sb.append("java\n");
		if (project.isOutputDebugEnabled())
		{
			// remote debug
			sb.append("-Xdebug\n");
			sb.append("-Xrunjdwp:server=y,transport=dt_socket,address=4000,suspend=y\n");
		}

		sb.append("-cp\n");
		sb.append("*\n");
		sb.append("org.crescendo.fmi.ShmServer\n");
		sb.append("-p");
		byte[] bytes = sb.toString().getBytes("UTF-8");
		InputStream source = new ByteArrayInputStream(bytes);
		project.createProjectTempRelativeFile(resourcesFolder + "/config.txt", source);
		bytes = info.modelDescription.getBytes("UTF-8");
		source = new ByteArrayInputStream(bytes);
		project.createProjectTempRelativeFile(resourcesFolder
				+ "/modelDescription.xml", source);

		project.copyResourcesToTempFolder(resourcesFolder);

		for (File unit : project.getSpecFiles())
		{
			String path = unit.getAbsolutePath().substring(project.getSourceRootPath().getAbsolutePath().length() + 1);
			project.createProjectTempRelativeFile(resourcesFolder + "/model/"
					+ path, FileUtils.openInputStream(unit));
		}

		// native

		String binaries = "binaries";

		for (String folderName : new String[] { "darwin64", "linux64",
				"linux32", "win32", "win64" })
		{
			String extension = ".so";
			if (folderName.startsWith("darwin"))
			{
				extension = ".dylib";
			} else if (folderName.startsWith("linux"))
			{
				extension = ".so";
			}
			if (folderName.startsWith("win"))
			{
				extension = ".dll";
			}

			is = SharedMemory.class.getClassLoader().getResourceAsStream("lib/vdm-tool-wrapper/binaries/"
					+ folderName + "/vdm-tool-wrapper" + extension);

			if (is != null)
			{
				project.createProjectTempRelativeFile(binaries + "/"
						+ folderName + "/" + name + extension, is);
			}

		}

		is = this.getClass().getResourceAsStream("/lib/vdm-tool-wrapper/binaries/git-info.txt");

		if (is != null)
		{
			project.createProjectTempRelativeFile(binaries + "/git-info-txt", is);
		}
	}

}
