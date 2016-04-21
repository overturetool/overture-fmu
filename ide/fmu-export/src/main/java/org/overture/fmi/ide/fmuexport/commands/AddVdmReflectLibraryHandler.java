package org.overture.fmi.ide.fmuexport.commands;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.overture.fmi.ide.fmuexport.FmuExportPlugin;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.ide.core.resources.IVdmProject;
import org.overture.ide.ui.utility.PluginFolderInclude;

public class AddVdmReflectLibraryHandler extends
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
					addVdmReflectLibrary(project);

				}
			}
		}

		return null;
	}

	public static void addVdmReflectLibrary(IVdmProject project)
	{
		IFolder libFolder = (IFolder) project.getModelBuildPath().getLibrary();

		if (!libFolder.exists())
		{
			try
			{
				libFolder.create(true, true, null);
			} catch (CoreException e1)
			{
				FmuExportPlugin.log(e1);
			}
		}
		try
		{
			URL tmp = PluginFolderInclude.getResource(IFmuExport.PLUGIN_ID, "jars/vdm-reflect-library.jar");

			IFile vdmReflectLibraryFile = libFolder.getFile("vdm-reflect-library.jar");

			if (!vdmReflectLibraryFile.exists())
			{
				vdmReflectLibraryFile.delete(true, null);
			}

			vdmReflectLibraryFile.create(tmp.openStream(), true, null);

			InputStream in = AddVdmReflectLibraryHandler.class.getClassLoader().getResourceAsStream("Reflect.vdmrt");

			IFile reflectLib = libFolder.getFile("Reflect.vdmrt");
			if (reflectLib.exists())
			{
				reflectLib.delete(true, null);
			}

			reflectLib.create(in, true, null);

			libFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (IOException e)
		{
			FmuExportPlugin.log(e);
		} catch (CoreException e)
		{
			FmuExportPlugin.log(e);
		}
	}

}