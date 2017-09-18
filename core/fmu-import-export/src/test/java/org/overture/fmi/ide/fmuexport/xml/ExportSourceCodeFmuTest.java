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
package org.overture.fmi.ide.fmuexport.xml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.overturetool.fmi.AbortException;
import org.overturetool.fmi.Main;
import org.xml.sax.SAXException;

public class ExportSourceCodeFmuTest
{
	private static String OS = System.getProperty("os.name").toLowerCase();

	@BeforeClass
	public static void configureMain()
	{
		Main.useExitCode = false;
	}

	@Test
	public void testExportSourceFmu() throws AbortException, IOException,
			InterruptedException, SAXException, ParserConfigurationException,
			XPathExpressionException
	{
		String output = ("target/" + this.getClass().getSimpleName() + "/" + getCurrentClassAndMethodNames()).replace('/', File.separatorChar);
		File inputDir = new File(new File(output), "input");
		FileUtils.copyDirectory(new File("src/test/resources/model".replace('/', File.separatorChar)), inputDir);
		Main.main(new String[] { "-name", "wt2", "-export", "source", "-root",
				inputDir.getAbsolutePath(), "-output", output, "-v" });

		File outputZip = new File(output + "/wt2.fmu");

		List<String> files = Collections.synchronizedList(new Vector<>());
		try (ZipFile zipFile = new ZipFile(outputZip);)
		{
			zipFile.stream().map(ZipEntry::getName).collect(Collectors.toCollection(() -> files));
		}

		String[] expectedFiles = new String[] {

				// base FMU sources
				"modelDescription.xml",
				"sources/CMakeLists.txt",
				// "sources/defines.def",
				"sources/fmi/fmi2Functions.h",
				"sources/fmi/fmi2FunctionTypes.h",
				"sources/fmi/fmi2TypesPlatform.h",
				"sources/main.c",
				"sources/Fmu.c",
				"sources/Fmu.h",
				"sources/FmuIO.c",
				"sources/FmuGUID.h",
				"sources/FmuModel.c",
				"sources/includes.txt",

				// FMI FMU model sources
				"sources/IntPort.c",
				"sources/IntPort.h",
				"sources/Port.c",
				"sources/Port.h",
				"sources/RealPort.c",
				"sources/RealPort.h",
				"sources/StringPort.c",
				"sources/StringPort.h",
				"sources/BoolPort.c",
				"sources/BoolPort.h",

				// VDM C library sources
				"sources/vdmlib/IOLib.c", "sources/vdmlib/IOLib.h",
				"sources/vdmlib/MATHLib.c", "sources/vdmlib/MATHLib.h",
				"sources/vdmlib/PatternBindMatch.c",
				"sources/vdmlib/PatternBindMatch.h",
				"sources/vdmlib/PrettyPrint.c", "sources/vdmlib/PrettyPrint.h",
				"sources/vdmlib/TypedValue.c", "sources/vdmlib/TypedValue.h",
				"sources/vdmlib/Vdm.h", "sources/vdmlib/VdmBasicTypes.c",
				"sources/vdmlib/VdmBasicTypes.h", "sources/vdmlib/VdmClass.c",
				"sources/vdmlib/VdmClass.h", "sources/vdmlib/VdmMap.c",
				"sources/vdmlib/VdmMap.h",
				"sources/vdmlib/VdmProduct.c",
				"sources/vdmlib/VdmProduct.h",
				"sources/vdmlib/VdmRecord.h",
				"sources/vdmlib/VdmSeq.c",
				"sources/vdmlib/VdmSeq.h",
				"sources/vdmlib/VdmSet.c",
				"sources/vdmlib/VdmSet.h",

				// VDM model sources
				"sources/ValveActuator.c", "sources/ValveActuator.h",
				"sources/WatertankSystem.c", "sources/WatertankSystem.h",
				"sources/World.c", "sources/World.h",
				"sources/HardwareInterface.c", "sources/HardwareInterface.h",
				"sources/Controller.c", "sources/Controller.h",
				"sources/VdmModelFeatures.h"

		};

		for (String string : expectedFiles)
		{
			Assert.assertTrue("Missing: " + string, files.contains(string));
		}

		// unzip
		File destination = new File(outputZip.getParentFile(), "zip");
		extract(outputZip, destination);
		checkCompile(new File(destination, "sources"));
	}

	public static boolean isMac()
	{
		return OS.indexOf("mac") >= 0;
	}

	private void checkCompile(File destination) throws InterruptedException,
			IOException
	{
		System.out.println(destination.getAbsolutePath());
		ProcessBuilder pb = new ProcessBuilder();
		pb.directory(destination);
		String cmake = "cmake";

		if (isMac())
		{
			cmake = "/usr/local/bin/cmake";
		}

		pb.command(cmake, ".");

		int exitCode = runExternalProcess(pb);
		Assert.assertEquals("Expected cmake to exit with code 0", 0, exitCode);

		pb = new ProcessBuilder();
		pb.directory(destination);
		pb.command("make", "-j4");

		exitCode = runExternalProcess(pb);
		Assert.assertEquals("Expected make to exit with code 0", 0, exitCode);

	}

	protected int runExternalProcess(ProcessBuilder pb) throws IOException,
			InterruptedException
	{

		Process p = pb.start();

		final InputStream inStream = p.getInputStream();
		new Thread(new Runnable()
		{
			public void run()
			{
				InputStreamReader reader = new InputStreamReader(inStream);
				Scanner scan = new Scanner(reader);
				while (scan.hasNextLine())
				{
					System.out.println(scan.nextLine());
				}
				scan.close();
			}
		}).start();

		final InputStream inErrStream = p.getErrorStream();
		new Thread(new Runnable()
		{
			public void run()
			{
				InputStreamReader reader = new InputStreamReader(inErrStream);
				Scanner scan = new Scanner(reader);
				while (scan.hasNextLine())
				{
					System.out.println(scan.nextLine());
				}
				scan.close();
			}
		}).start();

		if (!p.waitFor(1, TimeUnit.MINUTES))
		{
			// timeout - kill the process.
			p.destroy(); // consider using destroyForcibly instead
		}
		return p.exitValue();
	}

	public static String getCurrentClassAndMethodNames()
	{
		final StackTraceElement e = Thread.currentThread().getStackTrace()[2];
		final String s = e.getClassName();
		return s.substring(s.lastIndexOf('.') + 1, s.length()) + "."
				+ e.getMethodName();
	}

	@Test
	public void testExportSourceNoIOFmu() throws AbortException, IOException,
			InterruptedException, SAXException, ParserConfigurationException,
			XPathExpressionException
	{
		String output = ("target/" + getCurrentClassAndMethodNames()).replace('/', File.separatorChar);
		File inputDir = new File(new File(output), "input");
		FileUtils.copyDirectory(new File("src/test/resources/no-io".replace('/', File.separatorChar)), inputDir);
		Main.main(new String[] { "-name", "noio", "-export", "source", "-root",
				inputDir.getAbsolutePath(), "-output", output, "-v" });

		File outputZip = new File(output + "/noio.fmu");

		List<String> files = Collections.synchronizedList(new Vector<>());
		try (ZipFile zipFile = new ZipFile(outputZip);)
		{
			zipFile.stream().map(ZipEntry::getName).collect(Collectors.toCollection(() -> files));
		}

		String[] expectedFiles = new String[] {

				// base FMU sources
				"modelDescription.xml",
				"sources/CMakeLists.txt",
				// "sources/defines.def",
				"sources/fmi/fmi2Functions.h",
				"sources/fmi/fmi2FunctionTypes.h",
				"sources/fmi/fmi2TypesPlatform.h",
				"sources/main.c",
				"sources/Fmu.c",
				"sources/Fmu.h",
				"sources/FmuIO.c",
				"sources/FmuGUID.h",
				"sources/FmuModel.c",
				"sources/includes.txt",

				// FMI FMU model sources
				"sources/IntPort.c",
				"sources/IntPort.h",
				"sources/Port.c",
				"sources/Port.h",
				"sources/RealPort.c",
				"sources/RealPort.h",
				"sources/StringPort.c",
				"sources/StringPort.h",
				"sources/BoolPort.c",
				"sources/BoolPort.h",

				// VDM C library sources
				"sources/vdmlib/IOLib.c", "sources/vdmlib/IOLib.h",
				"sources/vdmlib/MATHLib.c", "sources/vdmlib/MATHLib.h",
				"sources/vdmlib/PatternBindMatch.c",
				"sources/vdmlib/PatternBindMatch.h",
				"sources/vdmlib/PrettyPrint.c", "sources/vdmlib/PrettyPrint.h",
				"sources/vdmlib/TypedValue.c", "sources/vdmlib/TypedValue.h",
				"sources/vdmlib/Vdm.h", "sources/vdmlib/VdmBasicTypes.c",
				"sources/vdmlib/VdmBasicTypes.h", "sources/vdmlib/VdmClass.c",
				"sources/vdmlib/VdmClass.h", "sources/vdmlib/VdmMap.c",
				"sources/vdmlib/VdmMap.h",
				"sources/vdmlib/VdmProduct.c",
				"sources/vdmlib/VdmProduct.h",
				"sources/vdmlib/VdmRecord.h",
				"sources/vdmlib/VdmSeq.c",
				"sources/vdmlib/VdmSeq.h",
				"sources/vdmlib/VdmSet.c",
				"sources/vdmlib/VdmSet.h",

				// VDM model sources
				"sources/ValveActuator.c", "sources/ValveActuator.h",
				"sources/WatertankSystem.c", "sources/WatertankSystem.h",
				"sources/World.c", "sources/World.h",
				"sources/HardwareInterface.c", "sources/HardwareInterface.h",
				"sources/Controller.c", "sources/Controller.h",
				"sources/VdmModelFeatures.h"

		};

		for (String string : expectedFiles)
		{
			Assert.assertTrue("Missing: " + string, files.contains(string));
		}

		// unzip
		File destination = new File(outputZip.getParentFile(), "zip");
		extract(outputZip, destination);
		checkCompile(new File(destination, "sources"));
	}

	protected void extract(File outputZip, File destination)
			throws ZipException, IOException, FileNotFoundException
	{
		ZipFile zipFile = new ZipFile(outputZip);
		try
		{
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements())
			{
				ZipEntry entry = entries.nextElement();
				File entryDestination = new File(destination, entry.getName());
				if (entry.isDirectory())
				{
					entryDestination.mkdirs();
				} else
				{
					entryDestination.getParentFile().mkdirs();
					InputStream in = zipFile.getInputStream(entry);
					OutputStream out = new FileOutputStream(entryDestination);
					IOUtils.copy(in, out);
					IOUtils.closeQuietly(in);
					out.close();
				}
			}
		} finally
		{
			zipFile.close();
		}
	}
}
