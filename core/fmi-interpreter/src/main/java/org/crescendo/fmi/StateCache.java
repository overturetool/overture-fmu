package org.crescendo.fmi;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.crescendo.fmi.xml.NodeIterator;
import org.destecs.core.vdmlink.LinkInfo;
import org.destecs.core.vdmlink.Links;
import org.destecs.protocol.structs.StepStructoutputsStruct;
import org.destecs.protocol.structs.StepinputsStructParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class StateCache
{

	final static Logger logger = LoggerFactory.getLogger(StateCache.class);

	public final double[] reals;
	public final int[] integers;
	public final boolean[] booleans;
	public final String[] strings;

	public final Links links;

	public StateCache(File linkFile) throws XPathExpressionException,
			DOMException, SAXException, IOException,
			ParserConfigurationException
	{
		links = createVdmLinks(linkFile);

		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		Document doc = docBuilderFactory.newDocumentBuilder().parse(linkFile);

		reals = new double[getMaxInt(doc, "//ScalarVariable[Real]/@valueReference") + 1];
		integers = new int[getMaxInt(doc, "//ScalarVariable[Integer]/@valueReference") + 1];
		booleans = new boolean[getMaxInt(doc, "//ScalarVariable[Boolean]/@valueReference") + 1];
		strings = new String[getMaxInt(doc, "//ScalarVariable[String]/@valueReference") + 1];
	}

	public List<StepinputsStructParam> collectInputsFromCache()
	{
		List<StepinputsStructParam> inputs = new Vector<StepinputsStructParam>();

		for (Entry<String, LinkInfo> entry : links.getInputs().entrySet())
		{

			Double value = 0.0;
			
			int index = Integer.valueOf(entry.getKey());
			switch (((ExtendedLinkInfo) entry.getValue()).type)
			{
				case Boolean:
					value = booleans[index] ? 1.0
							: 0.0;
					break;
				case Integer:
					value = new Double(integers[index]);
					break;
				case Real:
					value = reals[index];
					break;
				case String:
					value = strings[index];
					break;
				default:
					break;

			}

			inputs.add(new StepinputsStructParam(entry.getKey(), Arrays.asList(new Double[] { value }), Arrays.asList(new Integer[] { 1 })));
			logger.debug("Collecting inputs from cache name: '{}' value: '{}' size: '{}' valueref: '{}'", links.getBoundVariableInfo(entry.getKey()).getQualifiedNameString(), value, 1, entry.getKey());
		}

		return inputs;
	}

	public void syncOutputsToCache(List<StepStructoutputsStruct> outputs)
	{
		for (StepStructoutputsStruct output : outputs)
		{
			// System.out.println(String.format("\tOutput %s = %s", output.name,
			// output.value.get(0)));
			ExtendedLinkInfo link = (ExtendedLinkInfo) links.getLinks().get(output.name);

			Object vdmName = link.getQualifiedNameString();

			int index = Integer.valueOf(output.name);
			switch (link.type)
			{
				case Boolean:
					booleans[index] = output.value.get(0) > 0;
					logger.debug("Sync output to fmi struct name: '{}' value: '{}' valueref: '{}'", vdmName, booleans[index], index);
					break;
				case Integer:
					integers[index] = output.value.get(0).intValue();
					logger.debug("Sync output to fmi struct name: '{}' value: '{}' valueref: '{}'", vdmName, integers[index], index);
					break;
				case Real:
					reals[index] = output.value.get(0);
					logger.debug("Sync output to fmi struct name: '{}' value: '{}' valueref: '{}'", vdmName, reals[index], index);
					break;
				case String:

					// logger.debug("Sync output to fmi struct name: '{}' value: '{}' valueref: '{}'",vdmName,strings[index],index);
					break;
				default:
					break;

			}

		}

	}

	/**
	 * Utility method to calculate largest integer represented by the nodes obtained by the xpathquery
	 * 
	 * @param doc
	 * @param xpathQuery
	 * @return
	 * @throws XPathExpressionException
	 * @throws DOMException
	 */
	static int getMaxInt(Document doc, String xpathQuery)
			throws XPathExpressionException, DOMException
	{
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();

		int max = 0;

		for (Node n : new NodeIterator(lookup(doc, xpath, xpathQuery)))
		{

			String val = n.getNodeValue();
			try
			{
				int nv = Integer.parseInt(val);
				if (nv > max)
				{
					max = nv;
				}

			} catch (NumberFormatException e)
			{
			}

		}
		return max;
	}

	static Links createVdmLinks(File linkFile) throws SAXException,
			IOException, ParserConfigurationException,
			XPathExpressionException, DOMException
	{
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		Document doc = docBuilderFactory.newDocumentBuilder().parse(linkFile);

		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();

		final Map<String, LinkInfo> link = new HashMap<String, LinkInfo>();
		final List<String> outputs = new Vector<String>();
		final List<String> inputs = new Vector<String>();
		final List<String> designParameters = new Vector<String>();

		for (Node n : new NodeIterator(lookup(doc, xpath, "//ScalarVariable")))
		{
			NamedNodeMap attributes = n.getAttributes();
			String valRef = attributes.getNamedItem("valueReference").getNodeValue();

			String name = null;

			NodeList nameNodes = lookup(doc, xpath, "/fmiModelDescription/VendorAnnotations/Tool[@name='Overture']/Overture/link[@valueReference='"
					+ valRef + "']/@name");
			if (nameNodes != null && nameNodes.getLength() > 0)
			{

				name = nameNodes.item(0).getNodeValue();
			}

			if (name == null)
			{
				name = attributes.getNamedItem("name").getNodeValue();
			}

			List<String> qualifiedName = Arrays.asList(name.split("\\."));
			ExtendedLinkInfo.Type type = ExtendedLinkInfo.Type.Real;

			for (@SuppressWarnings("unused")
			Node n1 : new NodeIterator(lookup(n, xpath, "Real")))
			{
				type = ExtendedLinkInfo.Type.Real;
			}

			for (@SuppressWarnings("unused")
			Node n1 : new NodeIterator(lookup(n, xpath, "Boolean")))
			{
				type = ExtendedLinkInfo.Type.Boolean;
			}

			for (@SuppressWarnings("unused")
			Node n1 : new NodeIterator(lookup(n, xpath, "Integer")))
			{
				type = ExtendedLinkInfo.Type.Integer;
			}

			link.put(valRef, new ExtendedLinkInfo(valRef, qualifiedName, 0, type));

			String causality = attributes.getNamedItem("causality").getNodeValue();

			if ("output".equals(causality))
			{
				outputs.add(valRef);
			} else if ("input".equals(causality))
			{
				inputs.add(valRef);
			} else if ("parameter".equals(causality))
			{
				designParameters.add(valRef);
			}

		}

		return new Links(link, outputs, inputs, new Vector<String>(), designParameters, new Vector<String>());
	}

	final static boolean DEBUG = false;

	static NodeList lookup(Object doc, XPath xpath, String expression)
			throws XPathExpressionException
	{
		XPathExpression expr = xpath.compile(expression);

		if (DEBUG)
		{
			// System.out.println("Starting from: " + formateNodeWithAtt(doc));
		}
		final NodeList list = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

		if (DEBUG)
		{
			System.out.print("\tFound: ");
		}
		boolean first = true;
		for (@SuppressWarnings("unused")
		Node n : new NodeIterator(list))
		{
			if (DEBUG)
			{
				// System.out.println((!first ? "\t " : "")
				// + formateNodeWithAtt(n));
			}
			first = false;
		}
		if (first)
		{
			if (DEBUG)
			{
				System.out.println("none");
			}
		}
		return list;

	}
}
