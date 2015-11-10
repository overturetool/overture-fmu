package org.crescendo.fmi;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

public class LinksTest {

	@Test
	public void testLinks()
			throws XPathExpressionException, DOMException, SAXException, IOException, ParserConfigurationException {
		StateCache state = new StateCache(
				new File("src/test/resources/modelDescription.xml".replace('/', File.separatorChar)));

		Assert.assertEquals(1, state.collectInputsFromCache().size());
	}
}
