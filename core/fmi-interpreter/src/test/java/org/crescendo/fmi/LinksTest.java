/*
 * #%~
 * Fmi interface for the Crescendo Interpreter
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
package org.crescendo.fmi;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.overture.interpreter.runtime.ValueException;
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
			SAXException, IOException, ParserConfigurationException, ValueException
	{

		Assert.assertEquals(1, state.collectInputsFromCache().size());

		Assert.assertEquals("System.levelSensor.level", state.links.getBoundVariableInfo("3").getQualifiedNameString());
	}

	@Test
	public void testInputs() throws ValueException
	{

		List<NamedValue> inputs = state.collectInputsFromCache();

		for (NamedValue input : inputs)
		{
			List<String> qualiName = state.links.getQualifiedName(input.name);
			System.out.println(qualiName+ " = (new) = "+input.value);

		}
	}
}
