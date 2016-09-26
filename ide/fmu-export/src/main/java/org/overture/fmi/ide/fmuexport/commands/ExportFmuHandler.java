package org.overture.fmi.ide.fmuexport.commands;

import java.io.PrintStream;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.ide.core.resources.IVdmProject;
import org.overturetool.fmi.AbortException;
import org.overturetool.fmi.export.FmuExporter;

public class ExportFmuHandler extends org.eclipse.core.commands.AbstractHandler
{

	@Override
	public Object execute(ExecutionEvent event)
			throws org.eclipse.core.commands.ExecutionException
	{
		ISelection selections = HandlerUtil.getCurrentSelection(event);
		MessageConsole myConsole = ConsoleUtil.findConsole(IFmuExport.CONSOLE_NAME);

		if (selections instanceof IStructuredSelection)
		{
			IStructuredSelection ss = (IStructuredSelection) selections;

			Object o = ss.getFirstElement();
			if (o instanceof IAdaptable)
			{
				Shell activeShell = HandlerUtil.getActiveShell(event);
				IAdaptable a = (IAdaptable) o;
				IVdmProject project = (IVdmProject) a.getAdapter(IVdmProject.class);
				if (project != null)
				{
					try
					{
						MessageConsoleStream out = myConsole.newMessageStream();
						MessageConsoleStream err = myConsole.newMessageStream();
						err.setColor(new Color(activeShell.getDisplay(), 255, 0, 0));

						ProjectWrapper projectWrapper = new ProjectWrapper(activeShell, (IProject) a.getAdapter(IProject.class), project);

						generate(project, out, err, projectWrapper);
						((org.eclipse.core.resources.IProject) project.getAdapter(org.eclipse.core.resources.IProject.class)).refreshLocal(IResource.DEPTH_INFINITE, null);

						//projectWrapper.cleanUp();
					} catch (AbortException | CoreException e)
					{
					}
				}
			}
		}

		IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();// obtain the active page
		String id = IConsoleConstants.ID_CONSOLE_VIEW;

		try
		{
			IConsoleView view = (IConsoleView) page.showView(id);
			view.display(myConsole);
		} catch (PartInitException e)
		{
		}

		return null;
	}

	protected void generate(IVdmProject project, MessageConsoleStream out,
			MessageConsoleStream err, ProjectWrapper projectWrapper)
			throws AbortException
	{
		new FmuExporter().exportFmu(projectWrapper, project.getName(), new PrintStream(out), new PrintStream(err));
	}

	protected String getTitle()
	{
		return "FMU Export";
	}


}
