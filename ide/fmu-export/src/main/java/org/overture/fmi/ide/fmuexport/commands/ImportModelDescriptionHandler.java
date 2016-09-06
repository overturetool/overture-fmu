package org.overture.fmi.ide.fmuexport.commands;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;
import org.overture.fmi.ide.fmuexport.FmuExportPlugin;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.ide.core.resources.IVdmProject;
import org.overturetool.fmi.imports.ImportModelDescriptionProcesser;
import org.xml.sax.SAXException;

public class ImportModelDescriptionHandler extends
		org.eclipse.core.commands.AbstractHandler
{

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{

		ISelection selections = HandlerUtil.getCurrentSelection(event);
		MessageConsole myConsole = ConsoleUtil.findConsole(IFmuExport.CONSOLE_NAME);

		if (selections instanceof IStructuredSelection)
		{
			IStructuredSelection ss = (IStructuredSelection) selections;

			Object o = ss.getFirstElement();
			if (o instanceof IAdaptable)
			{
				IAdaptable a = (IAdaptable) o;
				IVdmProject project = (IVdmProject) a.getAdapter(IVdmProject.class);
				if (project != null)
				{
					Shell activeShell = HandlerUtil.getActiveShell(event);
					FileDialog dialog = new FileDialog(activeShell, SWT.OPEN);
					dialog.setFilterExtensions(new String[] { "*.xml" });
					String result = dialog.open();
					if (result != null)
					{
						MessageConsoleStream out = myConsole.newMessageStream();
						MessageConsoleStream err = myConsole.newMessageStream();

						err.setColor(new Color(activeShell.getDisplay(), 255, 0, 0));
						try
						{
							ProjectWrapper projectWrapper = new ProjectWrapper(activeShell, (IProject) a.getAdapter(IProject.class), project);

							new ImportModelDescriptionProcesser(new PrintStream(out), new PrintStream(err)).importFromXml(projectWrapper, new File(result));
							
							projectWrapper.cleanUp();
						} catch (SAXException e)
						{
							FmuExportPlugin.log(e);
						} catch (IOException e)
						{
							FmuExportPlugin.log(e);
						} catch (ParserConfigurationException e)
						{
							FmuExportPlugin.log(e);
						}
					}
//					AddVdmFmiLibraryHandler.addVdmFmiLibrary(project);
				}
			}
		}

		return null;
	}

}
