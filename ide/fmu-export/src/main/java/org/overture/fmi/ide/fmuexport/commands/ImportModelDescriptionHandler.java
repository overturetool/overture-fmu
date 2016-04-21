package org.overture.fmi.ide.fmuexport.commands;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.overture.ide.core.resources.IVdmProject;

public class ImportModelDescriptionHandler extends
		org.eclipse.core.commands.AbstractHandler
{

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{

		ISelection selections = HandlerUtil.getCurrentSelection(event);
		// MessageConsole myConsole = findConsole(CONSOLE_NAME);

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

				}
			}
		}

		return null;
	}

}
