package org.crescendo.fmi;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.destecs.protocol.structs.StepinputsStructParam;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

public class LinksTest
{

	StateCache state = null;

	@Before
	public void setup() throws XPathExpressionException, DOMException,
			SAXException, IOException, ParserConfigurationException
	{
		state = new StateCache(new File("src/test/resources/modelDescription.xml".replace('/', File.separatorChar)));
	}

	@Test
	public void testLinks() throws XPathExpressionException, DOMException,
			SAXException, IOException, ParserConfigurationException
	{

		Assert.assertEquals(1, state.collectInputsFromCache().size());

		Assert.assertEquals("System.levelSensor.level", state.links.getBoundVariableInfo("3").getQualifiedNameString());
	}

	@Test
	public void testInputs()
	{

		List<StepinputsStructParam> inputs = state.collectInputsFromCache();

		for (StepinputsStructParam input : inputs)
		{
			List<String> qualiName = state.links.getQualifiedName(input.name);
			System.out.println(qualiName+ " = (new) = "+input.value);

		}
	}
}
