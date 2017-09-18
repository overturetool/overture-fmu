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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.overturetool.fmi.Main;

public class Tracability
{
	public static String getCurrentTimeStamp()
	{
		DateFormat df = new SimpleDateFormat("yyyy-dd-MM HH:mm:ss");
		String now = df.format(new Date());
		return now;
	}
	
	public static String calculateGitHash(File file) throws IOException
	{
		try (InputStream fileInputStream = new FileInputStream(file);)
		{
			byte[] prefix = ("blob " + file.length() + "\0").getBytes("UTF-8");

			InputStream stream = new SequenceInputStream(new ByteArrayInputStream(prefix), fileInputStream);

			DigestInputStream dis = new DigestInputStream(stream, MessageDigest.getInstance("SHA-1"));
			BufferedInputStream bis = new BufferedInputStream(dis);
			while (bis.read() != -1)
				;
			return new BigInteger(1, dis.getMessageDigest().digest()).toString(16);
		} catch (NoSuchAlgorithmException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	
	public static String getToolId() throws IOException{
		Properties prop = new Properties();
		InputStream coeProp = Main.class.getResourceAsStream("/fmu-import-export.properties");
		prop.load(coeProp);
		
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"name\":");
		sb.append("\""+prop.getProperty("artifactId")+"\"");
		sb.append(",\"version\":");
		sb.append("\""+prop.getProperty("version")+"\"");
		sb.append("}");
		return sb.toString();
	}
}
