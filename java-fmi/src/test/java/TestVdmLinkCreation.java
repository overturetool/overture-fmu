import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.destecs.core.vdmlink.Links;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

public class TestVdmLinkCreation
{
	@Test
	public void ParseLinks() throws XPathExpressionException, DOMException,
			SAXException, IOException, ParserConfigurationException
	{
		File linkFile = new File("src/test/resources/modelDescription.xml".replace('/', File.separatorChar));

		Links links = Fmi2Java.createVdmLinks(linkFile);
		Assert.assertFalse("must not be empty", links.getLinks().isEmpty());
	}
}
