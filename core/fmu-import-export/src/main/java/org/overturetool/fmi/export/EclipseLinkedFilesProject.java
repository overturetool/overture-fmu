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
package org.overturetool.fmi.export;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.overturetool.fmi.export.xml.NodeIterator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class EclipseLinkedFilesProject
{
	public static List<File> getFiles(File projectFile) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException
	{
		
		List<File> links= new Vector<>();

		InputStream xmlInputStream = new FileInputStream(projectFile);

		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();

		final Document doc = docBuilderFactory.newDocumentBuilder().parse(xmlInputStream);

		XPathFactory xPathfactory = XPathFactory.newInstance();
		final XPath xpath = xPathfactory.newXPath();
		
		for (Node n : new NodeIterator(lookup(doc, xpath, "//linkedResources/link/locationURI")))
		{
			String path = n.getTextContent();
			if(path.contains("PARENT-1-PROJECT_LOC"))
			{
				path = path.replace("PARENT-1-PROJECT_LOC", "..");
			}
			
			File f = new File(projectFile.getParentFile(),path.replace('/', File.separatorChar));
			links.add(f.getCanonicalFile());
		}
		
		return links;
	}
	
	static Node lookupSingle(Object doc, XPath xpath, String expression)
			throws XPathExpressionException
	{
		NodeList list = lookup(doc, xpath, expression);
		if (list != null)
		{
			return list.item(0);
		}
		return null;
	}

	static NodeList lookup(Object doc, XPath xpath, String expression)
			throws XPathExpressionException
	{
		XPathExpression expr = xpath.compile(expression);

		final NodeList list = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

		return list;

	}
}
