package org.overture.fmi.ide.fmuexport.commands;

import java.io.PrintStream;

import org.eclipse.ui.console.MessageConsoleStream;
import org.overture.ide.core.resources.IVdmProject;
import org.overturetool.fmi.AbortException;
import org.overturetool.fmi.export.FmuSourceCodeExporter;


public class ExportSourceCodeFmuHandler extends ExportFmuHandler
{

	@Override
	protected String getTitle()
	{
		return "Source Code FMU";
	}
	
	

	protected void generate(IVdmProject project, MessageConsoleStream out,
			MessageConsoleStream err, ProjectWrapper projectWrapper)
			throws AbortException
	{
		new FmuSourceCodeExporter().exportFmu(projectWrapper, project.getName(), new PrintStream(out), new PrintStream(err));
	}


}
