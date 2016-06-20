package org.overture.fmi.ide.fmuexport.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
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
import org.overture.ast.analysis.AnalysisException;
import org.overture.ast.definitions.ASystemClassDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.lex.Dialect;
import org.overture.fmi.annotation.FmuAnnotation;
import org.overture.fmi.ide.fmuexport.FmuCompressor;
import org.overture.fmi.ide.fmuexport.FmuExportPlugin;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.fmi.ide.fmuexport.commands.ModelDescriptionGenerator.GeneratorInfo;
import org.overture.ide.core.IVdmModel;
import org.overture.ide.core.ast.NotAllowedException;
import org.overture.ide.core.resources.IVdmProject;
import org.overture.ide.core.resources.IVdmSourceUnit;
import org.overture.ide.ui.utility.PluginFolderInclude;
import org.overture.ide.ui.utility.VdmTypeCheckerUi;

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
				IAdaptable a = (IAdaptable) o;
				IVdmProject project = (IVdmProject) a.getAdapter(IVdmProject.class);
				if (project != null)
				{
					try
					{
						exportFmu(project, myConsole, HandlerUtil.getActiveShell(event));
					} catch (AbortException e)
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

	protected String getTitle()
	{
		return "FMU Export";
	}

	private void exportFmu(IVdmProject project, MessageConsole myConsole,
			Shell shell) throws AbortException
	{
		MessageConsoleStream out = myConsole.newMessageStream();
		MessageConsoleStream err = myConsole.newMessageStream();

		err.setColor(new Color(shell.getDisplay(), 255, 0, 0));
		out.println("\n---------------------------------------");
		out.println("|             " + getTitle() + "             |");
		out.println("---------------------------------------");
		out.println("Starting FMU export for project: '" + project.getName()
				+ "'");

		final IVdmModel model = project.getModel();
		if (model.isParseCorrect())
		{

			if (model == null || !model.isTypeCorrect())
			{
				VdmTypeCheckerUi.typeCheck(shell, project);
			}

			if (model.isTypeCorrect() && project.getDialect() == Dialect.VDM_RT)
			{

				try
				{
					Map<PDefinition, FmuAnnotation> definitionAnnotation = new VdmAnnotationProcesser().collectAnnotatedDefinitions(project, out, err);

					ASystemClassDefinition system = null;
					for (SClassDefinition cDef : model.getClassList())
					{
						if (cDef instanceof ASystemClassDefinition)
						{
							system = (ASystemClassDefinition) cDef;
							out.println("Found system class: '"
									+ cDef.getName().getName() + "'");
						}
					}

					ModelDescriptionGenerator generator = new ModelDescriptionGenerator(model.getClassList(), system);

					GeneratorInfo info = generator.generate(definitionAnnotation, project, out, err);

					out.println("\n########################\n Model Description: \n");
					out.println(info.modelDescription);

					IFolder outputContainer = (IFolder) project.getModelBuildPath().getOutput();

					if (!outputContainer.exists())
					{
						outputContainer.create(IResource.NONE, true, null);
					}

					IFolder fmus = outputContainer.getFolder("fmus");
					if (!fmus.exists())
					{
						fmus.create(IResource.NONE, true, null);
					}

					IFolder thisFmu = fmus.getFolder(project.getName());
					if (thisFmu.exists())
					{
						thisFmu.delete(true, null);
					}

					thisFmu.create(IResource.NONE, true, null);

					IFile thisModelDescription = thisFmu.getFile("modelDescription.xml");
					if (!thisModelDescription.exists())
					{
						byte[] bytes = info.modelDescription.getBytes("UTF-8");
						InputStream source = new ByteArrayInputStream(bytes);
						thisModelDescription.create(source, IResource.NONE, null);
					}

					copyFmuResources(info,thisFmu, project.getName(), project);

					final File fmuArchieveName = new File(outputContainer.getProject().getLocation().toFile().getAbsolutePath()
							+ File.separatorChar + project.getName() + ".fmu");
					final File fmuFolderPath = thisFmu.getLocation().toFile();

					Job job = new Job("Compressing FMU")
					{

						@Override
						protected IStatus run(IProgressMonitor monitor)
						{
							try
							{
								monitor.beginTask("Compressing", IProgressMonitor.UNKNOWN);
								FmuCompressor.compress(fmuFolderPath, fmuArchieveName);
								monitor.done();
							} catch (Exception e)
							{
								return new Status(Status.ERROR, IFmuExport.PLUGIN_ID, "Error compressing fmu", e);
							}
							return Status.OK_STATUS;
						}
					};

					job.schedule(0);

					// PluginFolderInclude.writeFile(libFolder, newName, io);

				} catch (NotAllowedException e)
				{
					FmuExportPlugin.log(e);
				} catch (IOException e)
				{
					FmuExportPlugin.log(e);
				} catch (CoreException e)
				{
					FmuExportPlugin.log(e);
				} catch (AnalysisException e1)
				{
					FmuExportPlugin.log(e1);
				}
			}
		}

	}

	protected void copyFmuResources(GeneratorInfo info, IFolder thisFmu, String name,
			IVdmProject project) throws CoreException, IOException, AnalysisException, NotAllowedException
	{
		final IFolder resourcesFolder = thisFmu.getFolder("resources");
		if (!resourcesFolder.exists())
		{
			resourcesFolder.create(IResource.NONE, true, null);
		}

		InputStream is = null;
		final String interpreterJarName = "fmi-interpreter-jar-with-dependencies.jar";

		URL tmp = PluginFolderInclude.getResource(IFmuExport.PLUGIN_ID, "jars/"
				+ interpreterJarName);

		is = tmp.openStream();

		IFile interpreter = resourcesFolder.getFile(interpreterJarName);
		if (!interpreter.exists())
		{
			interpreter.create(is, IResource.NONE, null);
		}

		IFile config = resourcesFolder.getFile("config.txt");
		if (config.exists())
		{
			config.delete(true, null);
		}
		StringBuffer sb = new StringBuffer();
		sb.append("false\n");
		sb.append("java\n");
		sb.append("-cp\n");
		sb.append("*\n");
		sb.append("org.crescendo.fmi.ShmServer\n");
		sb.append("-p");
		byte[] bytes = sb.toString().getBytes("UTF-8");
		InputStream source = new ByteArrayInputStream(bytes);
		config.create(source, IResource.NONE, null);

		// specification
		IFolder sources = thisFmu.getFolder("sources");
		if (!sources.exists())
		{
			sources.create(IResource.NONE, true, null);
		}

		IContainer lib = project.getModelBuildPath().getLibrary();

		if (lib.exists())
		{
			lib.accept(new IResourceVisitor()
			{
				@Override
				public boolean visit(IResource resource) throws CoreException
				{
					if (resource.getType() == IResource.FOLDER)
					{
						return true;
					} else if (resource.getType() == IResource.FILE
							&& "jar".equalsIgnoreCase(resource.getFileExtension()))
					{
						IFile target = resourcesFolder.getFile(resource.getName());
						resource.copy(target.getFullPath(), true, null);
					}
					return false;
				}
			});
		}

		for (IVdmSourceUnit unit : project.getSpecFiles())
		{
			IFile f = sources.getFile(unit.getFile().getProjectRelativePath());
			IFolder parent = (IFolder) f.getParent();
			if (!parent.exists())
			{
				parent.create(IResource.NONE, true, null);
			}
			if (!f.exists())
			{
				f.create(unit.getFile().getContents(), true, null);
			}
		}

		// native

		IFolder binaries = thisFmu.getFolder("binaries");
		if (!binaries.exists())
		{
			binaries.create(IResource.NONE, true, null);
		}

		for (String folderName : new String[] { "darwin64", "linux64",
				"linux32", "win32", "win64" })
		{
			IFolder folder = binaries.getFolder(folderName);
			if (!folder.exists())
			{
				folder.create(IResource.NONE, true, null);
			}

			String extension = ".so";
			if (folderName.startsWith("darwin"))
			{
				extension = ".dylib";
			} else if (folderName.startsWith("linux"))
			{
				extension = ".so";
			}
			if (folderName.startsWith("win"))
			{
				extension = ".dll";
			}

			is = this.getClass().getResourceAsStream("/fmu-shm-api/"
					+ folderName + "/vdm-tool-wrapper" + extension);

			if (is != null)
			{
				IFile bin = folder.getFile(name + extension);
				if (!bin.exists())
				{
					bin.create(is, true, null);
				}
			}

		}

	}

}
