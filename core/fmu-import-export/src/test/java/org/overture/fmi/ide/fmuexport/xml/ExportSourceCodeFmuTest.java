package org.overture.fmi.ide.fmuexport.xml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.overturetool.fmi.AbortException;
import org.overturetool.fmi.Main;
import org.xml.sax.SAXException;

public class ExportSourceCodeFmuTest
{
	@Test
	public void testExportSourceFmu() throws AbortException, IOException,
			InterruptedException, SAXException, ParserConfigurationException, XPathExpressionException
	{
		String output = "target/" + this.getClass().getSimpleName() + "/";
		FileUtils.copyDirectory(new File("src/test/resources/model"),new File( output));
		Main.main(new String[] { "-name", "wt2", "-export", "source", "-root",
				output, "-output", output, "-v" });
		

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
				"sources/defines.def",
				"sources/fmi/fmi2Functions.h",
				"sources/fmi/fmi2FunctionTypes.h",
				"sources/fmi/fmi2TypesPlatform.h",
				"sources/Fmu.c",
				"sources/Fmu.h",
				"sources/FmuIO.c",
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
				"sources/vdmlib/VdmMap.h", "sources/vdmlib/VdmProduct.c",
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
				"sources/Controller.c", "sources/Controller.h"

		};

		for (String string : expectedFiles)
		{
			Assert.assertTrue("Missing: " + string, files.contains(string));
		}

		// unzip
		ZipFile zipFile = new ZipFile(outputZip);
		try
		{
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements())
			{
				ZipEntry entry = entries.nextElement();
				File entryDestination = new File(outputZip.getParentFile(), entry.getName());
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
