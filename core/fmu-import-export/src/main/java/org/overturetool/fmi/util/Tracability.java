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
