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
package org.overturetool.fmi.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FolderCompressor
{

	public static void compress(File input, File output) throws IOException
	{
		if (output.getParentFile() != null)
		{
			output.getParentFile().mkdirs();
		}
		FileOutputStream fos = new FileOutputStream(output);

		ZipOutputStream zos = new ZipOutputStream(fos);

		scanAndAddEntries(zos, input, null);

		// close the ZipOutputStream
		zos.close();

	}

	static File[] scanAndAddEntries(ZipOutputStream zos, File folder,
			String base) throws IOException
	{

		List<File> items = new Vector<File>();

		if (base == null)
		{
			base = "";
		} else
		{
			base += "/";
		}

		for (File child : folder.listFiles())
		{
			if (child.isFile())
			{

				File fileToArchive = child;
				String name = base + child.getName();

				byte[] buffer = new byte[1024];

				FileInputStream fis = new FileInputStream(fileToArchive);

				zos.putNextEntry(new ZipEntry(name));

				int length;

				while ((length = fis.read(buffer)) > 0)
				{
					zos.write(buffer, 0, length);
				}

				zos.closeEntry();

				// close the InputStream
				fis.close();

				items.add(new File(base + child.getName()));

			} else
			{
				// items.add(new Item(base + child.getName() + File.separatorChar, 0, null));
				File[] childItems = scanAndAddEntries(zos, child, base
						+ child.getName());
				if (childItems != null)
				{
					for (File item : childItems)
					{
						items.add(item);
					}
				}
			}
		}

		return items.toArray(new File[items.size()]);
	}

}
