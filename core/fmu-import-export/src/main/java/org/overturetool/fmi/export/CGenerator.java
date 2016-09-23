package org.overturetool.fmi.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.overture.ast.analysis.AnalysisException;
import org.overture.ast.node.INode;
import org.overture.codegen.vdm2c.CGen;
import org.overture.config.Settings;
import org.overturetool.fmi.IProject;

public class CGenerator
{
	final IProject project;

	public CGenerator(IProject project)
	{
		this.project = project;
	}

	public void generate(File outputDir, PrintStream out, PrintStream err)
			throws AnalysisException
	{

		final CGen vdm2c = new CGen(outputDir);

		List<INode> nodes = new Vector<>();
		nodes.addAll(project.getClasses());

		// Generate user specified classes
		vdm2c.generate(nodes);

		out.println("Project dialect: " + Settings.dialect);

		out.println("Code generation completed successfully.");
		out.println("Copying native library files.");
		copyNativeLibFiles(new File(outputDir, "vdmlib"));

	}

	private void copyNativeLibFiles(File outfolder)
	{
		File outputFile = null;
		InputStream jarfile = null;
		FileOutputStream fos = null;
		JarInputStream jarstream = null;
		JarEntry filejarentry = null;

		if (!outfolder.exists())
		{
			outfolder.mkdir();
		}

		try
		{
			jarfile = this.getClass().getClassLoader().getResourceAsStream("jars/vdmclib.jar");
			jarstream = new JarInputStream(jarfile);
			filejarentry = jarstream.getNextJarEntry();

			// Simply step through the JAR containing the library files and extract only the code files.
			// These are copied to the source output folder.
			while (filejarentry != null)
			{
				if (!filejarentry.getName().contains("src/main")
						|| filejarentry.getName().contains("META"))
				{
					filejarentry = jarstream.getNextJarEntry();
					continue;
				}

				outputFile = new File(outfolder.toString()
						+ File.separator
						+ filejarentry.getName().replace("src/main"
								+ File.separator, ""));

				if (filejarentry.isDirectory())
				{
					filejarentry = jarstream.getNextJarEntry();
					continue;
				}
				if (filejarentry.getName().contains("SampleMakefile"))
				{
					filejarentry = jarstream.getNextJarEntry();
					continue;
				}

				outputFile.getParentFile().mkdirs();
				fos = new FileOutputStream(outputFile);

				while (jarstream.available() > 0)
				{
					int b = jarstream.read();
					if (b >= 0)
					{
						fos.write(b);
					}
				}
				fos.flush();
				fos.close();
				jarstream.closeEntry();
				filejarentry = jarstream.getNextJarEntry();

			}
			jarstream.close();
			jarfile.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

}
