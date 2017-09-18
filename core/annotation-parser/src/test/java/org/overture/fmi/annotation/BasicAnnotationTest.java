/*
 * #%~
 * Annotation parser for FMU export
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
package org.overture.fmi.annotation;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.antlr.runtime.tree.CommonTree;
import org.destecs.core.parsers.IError;
import org.junit.Assert;
import org.junit.Test;

public class BasicAnnotationTest
{

	public List<FmuAnnotation> parse(String data) throws IOException
	{
		AnnotationParserWrapper parser = new AnnotationParserWrapper();

		List<FmuAnnotation> ret = parser.parse(new File("test"), data);

		if (parser.hasErrors())
		{
			String tmp = "";
			for (IError err : parser.getErrors())
			{
				tmp += err + "\n";
			}
			Assert.fail(tmp);
		}
		return ret;

	}

	@Test
	public void testNameOnly() throws IOException
	{

		parse("--@ interface: name=\"a.b.c\" ;");

	}

	CommonTree getFirstChild(CommonTree o)
	{
		return ((CommonTree) o.getChildren().get(0));
	}

	@Test
	public void testOutput() throws IOException
	{

		List<FmuAnnotation> node = parse("--@ interface: type=output ;");

		Iterator<? extends Object> childItr = node.iterator();

		CommonTree child = ((FmuAnnotation) childItr.next()).getTree();

		Assert.assertEquals("interface", child.token.getText());

		Assert.assertEquals("type", getFirstChild(child).token.getText());
		Assert.assertEquals("output", getFirstChild(getFirstChild(child)).token.getText());

	}

	@Test
	public void testOutputX2() throws IOException
	{

		List<FmuAnnotation> node = parse("--@ interface: type=output ;\n --@ interface: type=output ;");


		Iterator<? extends Object> childItr = node.iterator();

		CommonTree child = ((FmuAnnotation) childItr.next()).getTree();

		Assert.assertEquals("interface", child.token.getText());

		Assert.assertEquals("type", getFirstChild(child).token.getText());
		Assert.assertEquals("output", getFirstChild(getFirstChild(child)).token.getText());

		child = ((FmuAnnotation) childItr.next()).getTree();

		Assert.assertEquals("interface", child.token.getText());

		Assert.assertEquals("type", getFirstChild(child).token.getText());
		Assert.assertEquals("output", getFirstChild(getFirstChild(child)).token.getText());
	}

	@Test
	public void testNameOutput() throws IOException
	{

		List<FmuAnnotation> node = parse("--@ interface:name=\"var1\" , type=output ;");


		Iterator<? extends Object> childItr = node.iterator();

		CommonTree child = ((FmuAnnotation) childItr.next()).getTree();

		Assert.assertEquals("interface", child.token.getText());

		Assert.assertEquals("name", getFirstChild(child).token.getText());
		Assert.assertEquals("var1", getFirstChild(getFirstChild(child)).token.getText());

		CommonTree secondChild = ((CommonTree) child.getChildren().get(1));

		Assert.assertEquals("type", secondChild.token.getText());
		Assert.assertEquals("output", getFirstChild(secondChild).token.getText());

	}

	@Test
	public void streamTest() throws IOException
	{

//		InputStream in = IOUtils.toInputStream("some \n-- @ source\n--@ ll", "UTF-8");
//
//		String tmp = IOUtils.toString(new RetainVdmCommentsFilter(in, "--@"));

		//System.out.println(tmp);
		// AnnotationParserWrapper parser = new AnnotationParserWrapper();

		// annotations_return ret = parser.parse(new File("test"), new RetainVdmCommentsFilter(in));
	}

	//@Test
//	public void streamTest2() throws IOException
//	{
//		InputStream in = new ByteArrayInputStream(FileUtils.readFileToByteArray(new File("/Users/kel/data/runtime-overture.product/fmu-test/sys.vdmrt")));
//		// InputStream in = IOUtils.toInputStream("some \n-- @ source\n", "UTF-8");
//
//		String tmp = IOUtils.toString(new RetainVdmCommentsFilter(in, "--@"));
//
//		//System.out.println(tmp);
//		AnnotationParserWrapper parser = new AnnotationParserWrapper();
//
//		in = new ByteArrayInputStream(FileUtils.readFileToByteArray(new File("/Users/kel/data/runtime-overture.product/fmu-test/sys.vdmrt")));
//		annotations_return ret = parser.parse(new File("test"), new RetainVdmCommentsFilter(in, "--@"));
//
//		if (parser.hasErrors())
//		{
//			String tmp1 = "";
//			for (IError err : parser.getErrors())
//			{
//				tmp1 += err + "\n";
//			}
//			Assert.fail(tmp1);
//		}
//
//	//	System.out.println(ret);
////		System.out.println("Tree: " + ret.getTree());
//	}

}
