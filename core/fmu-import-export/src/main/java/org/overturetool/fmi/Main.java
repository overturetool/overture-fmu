package org.overturetool.fmi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.lex.Dialect;
import org.overture.config.Release;
import org.overture.config.Settings;
import org.overture.parser.lex.LexException;
import org.overture.parser.syntax.ParserException;
import org.overture.typechecker.util.TypeCheckerUtil;
import org.overture.typechecker.util.TypeCheckerUtil.TypeCheckResult;
import org.overturetool.fmi.export.FmuExporter;
import org.overturetool.fmi.export.FmuSourceCodeExporter;
import org.overturetool.fmi.imports.ImportModelDescriptionProcesser;
import org.xml.sax.SAXException;

public class Main
{

	static boolean checkRequiredOptions(CommandLine cmd, Option... opt)
	{
		for (Option option : opt)
		{
			if (!cmd.hasOption(option.getOpt()))
			{
				System.err.println("Missing required option: "
						+ option.getOpt());
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) throws AbortException, IOException,
			InterruptedException, SAXException, ParserConfigurationException
	{
		Options options = new Options();
		Option helpOpt = Option.builder("h").longOpt("help").desc("Show this description").build();

		Option releaseOpt = Option.builder("r").longOpt("release").desc("Overture release version").hasArg().build();
		Option exportOpt = Option.builder("export").desc("Export").hasArg().numberOfArgs(1).argName("source> or <tool").build();
		Option importModelDescriptionOpt = Option.builder("import").longOpt("modeldescrption").desc("Import modelDescription.xml").hasArg().build();

		Option projectNameOpt = Option.builder("name").desc("Project name / FMU name").hasArg().build();
		Option projectRootOpt = Option.builder("root").desc("Project root directory").hasArg().build();
		Option outputFolderOpt = Option.builder("output").desc("Outout location").hasArg().build();
		Option forceOpt = Option.builder("f").longOpt("force").desc("Force override of existing output files").build();
		Option verboseOpt = Option.builder("v").longOpt("verbose").desc("Verbose mode or print diagnostic version info").build();
		Option versionOpt = Option.builder("V").longOpt("version").desc("Show version").build();

		options.addOption(helpOpt);
		options.addOption(releaseOpt);
		options.addOption(exportOpt);
		options.addOption(importModelDescriptionOpt);

		options.addOption(projectNameOpt);
		options.addOption(projectRootOpt);
		options.addOption(outputFolderOpt);
		options.addOption(verboseOpt);
		options.addOption(forceOpt);
		options.addOption(versionOpt);

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try
		{
			cmd = parser.parse(options, args);
		} catch (ParseException e1)
		{
			System.err.println("Parsing failed. Reason: " + e1.getMessage());
			showHelp(options);
			return;
		}

		if (cmd.hasOption(helpOpt.getOpt()))
		{
			showHelp(options);
			return;
		}

		// check option combinations

		boolean exportSourceFmu = false;
		boolean exportToolFmu = false;
		boolean force = cmd.hasOption(forceOpt.getOpt());
		boolean verbose = cmd.hasOption(verboseOpt.getOpt());
		boolean version = cmd.hasOption(versionOpt.getOpt());

		if (verbose || version)
		{
			showVersion();
			if (version)
			{
				return;
			}
		}

		if (!cmd.hasOption(projectRootOpt.getOpt())
				|| cmd.getOptionValue(projectRootOpt.getOpt()) == null)
		{
			System.err.println("Parsing failed. Reason: "
					+ "Missing required option: " + projectRootOpt.getOpt());

			showHelp(options);
			return;
		}

		if (cmd.hasOption(exportOpt.getOpt()))
		{
			if (!checkRequiredOptions(cmd, outputFolderOpt, projectRootOpt, projectNameOpt))
			{
				return;
			}

			String exportType = cmd.getOptionValue(exportOpt.getOpt());

			exportSourceFmu = "source".equals(exportType);
			exportToolFmu = "tool".equals(exportType);

			if (!(exportSourceFmu || exportToolFmu))
			{
				System.err.println("The -" + exportOpt.getOpt()
						+ " argument only accepts <source> or <tool>");
				return;
			}
		} else if (cmd.hasOption(importModelDescriptionOpt.getOpt()))
		{
			if (!checkRequiredOptions(cmd, projectRootOpt))
			{
				return;
			}
		}

		if (cmd.hasOption(releaseOpt.getOpt()))
		{
			Settings.release = cmd.getOptionValue(releaseOpt.getOpt()).equals("vdm10") ? Release.VDM_10
					: Release.CLASSIC;
		}

		String projectName = null;
		if (cmd.hasOption(projectNameOpt.getOpt()))
		{
			projectName = cmd.getOptionValue(projectNameOpt.getOpt());
		}
		File projectRoot = new File(cmd.getOptionValue(projectRootOpt.getOpt()));
		File outputFolder = null;

		if (cmd.hasOption(outputFolderOpt.getOpt()))
		{
			outputFolder = new File(cmd.getOptionValue(outputFolderOpt.getOpt()));
		}

		Settings.dialect = Dialect.VDM_RT;

		Collection<File> specFiles = new Vector<File>();
		if (projectRoot.exists())
		{
			specFiles = FileUtils.listFiles(projectRoot, new String[] { "vdmrt" }, true);
		}
		ConsoleProject project = new ConsoleProject(projectName, projectRoot, outputFolder, specFiles);

		PrintStream out = verbose ? System.out
				: new PrintStream(new NullOutputStream());

		if (cmd.hasOption(exportOpt.getOpt()))
		{

			File fmuFile = null;

			if (exportToolFmu)
			{
				fmuFile = new FmuExporter().exportFmu(project, projectName, out, System.err, force);
			} else if (exportSourceFmu)
			{
				fmuFile = new FmuSourceCodeExporter().exportFmu(project, projectName, out, System.err, force);
			}

			if (fmuFile == null)
			{
				System.err.println("Generation failed.");
				return;
			}

			// Process p = Runtime.getRuntime().exec("unzip -l "
			// + fmuFile.getAbsolutePath());
			// System.err.println(IOUtils.toString(p.getErrorStream()));
			// System.out.println(IOUtils.toString(p.getInputStream()));
			// p.waitFor();

			out.println("The zip contains: ");
			try (ZipFile zipFile = new ZipFile(fmuFile);)
			{
				zipFile.stream().map(ZipEntry::getName).forEach(out::println);
			}
		} else if (cmd.hasOption(importModelDescriptionOpt.getOpt()))
		{
			File md = new File(cmd.getOptionValue(importModelDescriptionOpt.getOpt()));
			new ImportModelDescriptionProcesser(out, System.err).importFromXml(project, md);
		}

		project.cleanUp();

	}

	private static void showVersion()
	{
		try
		{
			Properties prop = new Properties();
			InputStream coeProp = Main.class.getResourceAsStream("/fmu-import-export.properties");
			prop.load(coeProp);
			System.out.println("Tool: " + prop.getProperty("artifactId"));
			System.out.println("Version: " + prop.getProperty("version"));
		} catch (Exception e)
		{
		}

	}

	public static void showHelp(Options options)
	{
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("fmu-import-export", options);
	}

	public static class ConsoleProject implements IProject
	{

		private final String name;
		private final File sourceRootPath;

		private final File outputFolder;
		private final List<File> specFiles;
		private File tempFolder = null;
		private List<? extends SClassDefinition> classes;

		public ConsoleProject(String name, File sourceRoot, File outputFolder,
				Collection<File> specFiles)
		{
			super();
			this.name = name;
			this.sourceRootPath = sourceRoot;
			this.outputFolder = outputFolder;
			this.specFiles = new Vector<File>();
			this.specFiles.addAll(specFiles);
		}

		@Override
		public String getName()
		{
			return name;
		}

		@Override
		public File getSourceRootPath()
		{
			return this.sourceRootPath;
		}

		@Override
		public boolean typeCheck()
		{
			if (specFiles.isEmpty())
			{
				this.classes = new Vector<SClassDefinition>();
				return true;
			}
			try
			{
				TypeCheckResult<List<SClassDefinition>> res = TypeCheckerUtil.typeCheckRt(getSpecFiles(), "UTF-8");
				this.classes = res.result;
				if (!res.parserResult.errors.isEmpty())
				{
					System.err.println(res.parserResult.getErrorString());
				}
				if (!res.errors.isEmpty())
				{
					System.err.println(res.getErrorString());
				}

				return res.parserResult.errors.isEmpty()
						&& res.errors.isEmpty();

			} catch (ParserException | LexException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return false;
		}

		@Override
		public List<? extends SClassDefinition> getClasses()
		{
			return this.classes;
		}

		@Override
		public void createProjectTempRelativeFile(String path,
				InputStream content) throws IOException
		{
			File file = new File(getTempFolder(), path.replace('/', File.separatorChar));

			FileOutputStream outStream = null;
			try
			{
				outStream = FileUtils.openOutputStream(file);
				try
				{
					IOUtils.copy(content, outStream);
				} catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} finally
			{
				IOUtils.closeQuietly(outStream);
				IOUtils.closeQuietly(content);
			}
		}

		@Override
		public void createSpecFileProjectRelative(String path,
				InputStream content) throws IOException
		{
			File file = new File(getSourceRootPath(), path.replace('/', File.separatorChar));
			try
			{
				IOUtils.copy(content, FileUtils.openOutputStream(file));
				this.specFiles.add(file);
			} catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void log(Exception exception)
		{
			exception.printStackTrace();
		}

		@Override
		public void log(String message, Exception exception)
		{
			System.err.println(message);
			exception.printStackTrace();

		}

		@Override
		public List<File> getSpecFiles()
		{
			return this.specFiles;
		}

		@Override
		public void deleteMarker(File unit)
		{

		}

		@Override
		public void addMarkser(File unit, String message, int line,
				MarkerType error)
		{
			System.err.println(unit.getName() + ": " + message + ":" + line);
		}

		@Override
		public File getOutputFolder()
		{
			return this.outputFolder;
		}

		@Override
		public void scheduleJob(IJob job)
		{
			job.run();
		}

		@Override
		public void copyResourcesToTempFolder(String resourcesFolder)
		{

		}

		@Override
		public File getTempFolder() throws IOException
		{
			if (tempFolder == null)
			{
				tempFolder = Files.createTempDirectory("overture-fmu").toFile();
			}
			return tempFolder;
		}

		@Override
		public void cleanUp() throws IOException
		{
			if (tempFolder != null)
			{
				FileUtils.deleteDirectory(tempFolder);
			}

		}

		@Override
		public boolean isOutputDebugEnabled()
		{
			return false;
		}

	}

}
