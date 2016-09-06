package org.overture.fmi.ide.fmuexport.xml;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.overture.ast.definitions.AInstanceVariableDefinition;
import org.overture.ast.definitions.AValueDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.expressions.ANewExp;
import org.overture.ast.expressions.PExp;
import org.overture.ast.lex.Dialect;
import org.overture.config.Release;
import org.overture.config.Settings;
import org.overture.parser.lex.LexException;
import org.overture.parser.syntax.ParserException;
import org.overture.typechecker.util.TypeCheckerUtil;
import org.overture.typechecker.util.TypeCheckerUtil.TypeCheckResult;
import org.overturetool.fmi.AbortException;
import org.overturetool.fmi.Main;
import org.xml.sax.SAXException;

public class ImportModelDescriptionTest
{
	@Test
	public void testImportEmpty() throws AbortException, IOException,
			InterruptedException, SAXException, ParserConfigurationException,
			ParserException, LexException
	{
		String output = "target/" + this.getClass().getSimpleName() + "/testImportEmpty/";
		importSingleEmpty(output,"src/test/resources/modelDescription.xml");

	}


	private void importSingleEmpty(String output,String modelDescriptionPath) throws AbortException, IOException,
			InterruptedException, SAXException, ParserConfigurationException,
			ParserException, LexException
	{
		Main.main(new String[] { "-name", "wt2", "-import",
				modelDescriptionPath, "-root", output });

		List<File> specFiles = new Vector<>();
		specFiles.addAll(FileUtils.listFiles(new File(output), new String[] { "vdmrt" }, true));
		Settings.dialect = Dialect.VDM_RT;
		Settings.release = Release.VDM_10;

		TypeCheckResult<List<SClassDefinition>> res = TypeCheckerUtil.typeCheckRt(specFiles);

		Assert.assertTrue(res.parserResult.getErrorString()
				+ res.getErrorString(), res.parserResult.errors.isEmpty()
				&& res.errors.isEmpty());

		boolean foundHardwareInterfaceClass = false;
		for (SClassDefinition cDef : res.result)
		{
			if (cDef.getName().getName().equals("HardwareInterface"))
			{
				foundHardwareInterfaceClass = true;
				checkDef(cDef.getDefinitions(), "level", "RealPort", TypeCheckerUtil.typeCheckExpression("0.0").result);
				checkDef(cDef.getDefinitions(), "valve", "BoolPort", TypeCheckerUtil.typeCheckExpression("false").result);
				checkDef(cDef.getDefinitions(), "minlevel", "RealPort", TypeCheckerUtil.typeCheckExpression("1.0").result);
				checkDef(cDef.getDefinitions(), "maxlevel", "RealPort", TypeCheckerUtil.typeCheckExpression("2.0").result);
			}

		}
		Assert.assertTrue("no hardware interface class found", foundHardwareInterfaceClass);
	}

	@Test
	public void testReImport() throws ParserException, LexException, AbortException, IOException, InterruptedException, SAXException, ParserConfigurationException
	{
		String output = "target/" + this.getClass().getSimpleName() + "/testReImport/";
		importSingleEmpty(output,"src/test/resources/modelDescription.xml");
		importSingleEmpty(output,"src/test/resources/modelDescription.xml");
	}
	
	@Test
	public void testImportImportMerge() throws ParserException, LexException, AbortException, IOException, InterruptedException, SAXException, ParserConfigurationException
	{
		String output = "target/" + this.getClass().getSimpleName() + "/testImportImportMerge/";
		importSingleEmpty(output,"src/test/resources/modelDescription.xml");
		importSingleEmpty(output,"src/test/resources/modelDescription2.xml");
	}
	
	private void checkDef(List<PDefinition> defs, String name,
			String expectedType, PExp... args)
	{
		boolean found = false;
		PExp init = null;

		for (PDefinition def : defs)
		{
			if (def != null && def.getName() != null
					&& def.getName().getName().equals(name))
			{
				found = true;
			} else if (def != null && def instanceof AValueDefinition)
			{
				AValueDefinition vDef = (AValueDefinition) def;
				if (vDef.getPattern().toString().equals(name))
				{
					found = true;
					init = vDef.getExpression();
				}
			}

			if (found && def instanceof AInstanceVariableDefinition)
			{
				init = ((AInstanceVariableDefinition) def).getExpression();
			}

			if (found)
			{
				Assert.assertNotNull(init);
				Assert.assertEquals("Initial type does not match for: " + name, expectedType, init.getType().toString());
				Assert.assertTrue("the intial expression must be 'new'", init instanceof ANewExp);
				if (init instanceof ANewExp)
				{
					ANewExp nExp = (ANewExp) init;
					Assert.assertEquals("number of argument does not martch", nExp.getArgs().size(), args == null ? 0
							: args.length);
					if (args != null)
					{
						for (int i = 0; i < args.length; i++)
						{
							Assert.assertEquals(nExp.getArgs().get(i), args[i]);

						}
					}
				}
				break;
			}
		}
		Assert.assertTrue("could not find definition: " + name, found);

	}
}
