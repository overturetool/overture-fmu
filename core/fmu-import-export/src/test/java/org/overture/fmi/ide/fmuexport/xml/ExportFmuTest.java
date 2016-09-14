package org.overture.fmi.ide.fmuexport.xml;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Assert;
import org.junit.Test;
import org.overturetool.fmi.AbortException;
import org.overturetool.fmi.Main;
import org.xml.sax.SAXException;

public class ExportFmuTest
{
	@Test
	public void testExportFmu() throws AbortException, IOException,
			InterruptedException, SAXException, ParserConfigurationException
	{
		String output = "target/" + this.getClass().getSimpleName() + "/";
		Main.main(new String[] { "-name", "wt2", "-export", "-t", "-root",
				"src/test/resources/model", "-output", output });

		File outputZip = new File(output + "/wt2.fmu");

		List<String> files = Collections.synchronizedList(new Vector<>());
		try (ZipFile zipFile = new ZipFile(outputZip);)
		{
			zipFile.stream().map(ZipEntry::getName).collect(Collectors.toCollection(() -> files));
		}

		String[] expectedFiles = new String[] {

		"binaries/darwin64/wt2.dylib", "binaries/linux32/wt2.so",
				"binaries/linux64/wt2.so", "binaries/win32/wt2.dll",
				"binaries/win64/wt2.dll", "modelDescription.xml",
				"resources/config.txt",
				"resources/fmi-interpreter-jar-with-dependencies.jar",
				"resources/model/Controller.vdmrt",
				"resources/model/HardwareInterface.vdmrt",
				"resources/model/LevelSensor.vdmrt",
				"resources/model/lib/Fmi.vdmrt",
				"resources/model/lib/IO.vdmrt", "resources/model/System.vdmrt",
				"resources/model/ValveActuator.vdmrt",
				"resources/model/World.vdmrt", "resources/modelDescription.xml"

		};

		for (String string : expectedFiles)
		{
			Assert.assertTrue("Missing: " + string, files.contains(string));
		}
	}
	
	
	@Test
	public void testExportFmuNoName() throws AbortException, IOException,
			InterruptedException, SAXException, ParserConfigurationException
	{
		String output = "target/" + this.getClass().getSimpleName() + "/";
		Main.main(new String[] { "-name", "wt2", "-export", "-t", "-root",
				"src/test/resources/model_no_name", "-output", output });

		File outputZip = new File(output + "/wt2.fmu");

		List<String> files = Collections.synchronizedList(new Vector<>());
		try (ZipFile zipFile = new ZipFile(outputZip);)
		{
			zipFile.stream().map(ZipEntry::getName).collect(Collectors.toCollection(() -> files));
		}

		String[] expectedFiles = new String[] {

		"binaries/darwin64/wt2.dylib", "binaries/linux32/wt2.so",
				"binaries/linux64/wt2.so", "binaries/win32/wt2.dll",
				"binaries/win64/wt2.dll", "modelDescription.xml",
				"resources/config.txt",
				"resources/fmi-interpreter-jar-with-dependencies.jar",
				"resources/model/Controller.vdmrt",
				"resources/model/HardwareInterface.vdmrt",
				"resources/model/LevelSensor.vdmrt",
				"resources/model/lib/Fmi.vdmrt",
				"resources/model/lib/IO.vdmrt", "resources/model/System.vdmrt",
				"resources/model/ValveActuator.vdmrt",
				"resources/model/World.vdmrt", "resources/modelDescription.xml"

		};

		for (String string : expectedFiles)
		{
			Assert.assertTrue("Missing: " + string, files.contains(string));
		}
	}
}
