package org.overture.fmi.ide.fmuexport.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.destecs.core.parsers.IError;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
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
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;
import org.overture.ast.definitions.AInstanceVariableDefinition;
import org.overture.ast.definitions.ASystemClassDefinition;
import org.overture.ast.definitions.AValueDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.lex.Dialect;
import org.overture.ast.types.ABooleanBasicType;
import org.overture.ast.types.SNumericBasicType;
import org.overture.fmi.annotation.AnnotationParserWrapper;
import org.overture.fmi.annotation.FmuAnnotation;
import org.overture.fmi.annotation.RetainVdmCommentsFilter;
import org.overture.fmi.ide.fmuexport.FmuCompressor;
import org.overture.fmi.ide.fmuexport.FmuExportPlugin;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.ide.core.IVdmModel;
import org.overture.ide.core.ast.NotAllowedException;
import org.overture.ide.core.resources.IVdmProject;
import org.overture.ide.core.resources.IVdmSourceUnit;
import org.overture.ide.core.utility.FileUtility;
import org.overture.ide.ui.utility.PluginFolderInclude;
import org.overture.ide.ui.utility.VdmTypeCheckerUi;

public class ExportFmuHandler extends org.eclipse.core.commands.AbstractHandler
{

	private static final String CONSOLE_NAME = "ExportFmuConsole";

	@Override
	public Object execute(ExecutionEvent event)
			throws org.eclipse.core.commands.ExecutionException
	{
		ISelection selections = HandlerUtil.getCurrentSelection(event);
		MessageConsole myConsole = findConsole(CONSOLE_NAME);

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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private void exportFmu(IVdmProject project, MessageConsole myConsole,
			Shell shell) throws AbortException
	{
		MessageConsoleStream out = myConsole.newMessageStream();
		MessageConsoleStream err = myConsole.newMessageStream();

		err.setColor(new Color(shell.getDisplay(), 255, 0, 0));
		out.println("\n---------------------------------------");
		out.println("|             FMU Export              |");
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
					Map<PDefinition, FmuAnnotation> definitionAnnotation = collectAnnotatedDefinitions(project, out, err);

					ASystemClassDefinition system = null;
					// List<SClassDefinition> classesWithExports = new Vector<SClassDefinition>();
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

					String modelDescription = generator.generate(definitionAnnotation, project, out, err);

					out.println("\n########################\n Model Description: \n");
					out.println(modelDescription);

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
						byte[] bytes = modelDescription.getBytes("UTF-8");
						InputStream source = new ByteArrayInputStream(bytes);
						thisModelDescription.create(source, IResource.NONE, null);
					}

					copyFmuWrapper(thisFmu, project.getName(), project);

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
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CoreException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}

	private void copyFmuWrapper(IFolder thisFmu, String name,
			IVdmProject project) throws CoreException, IOException
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
					+ folderName + "/libshmfmu" + extension);

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

	public Map<PDefinition, FmuAnnotation> collectAnnotatedDefinitions(
			IVdmProject project, MessageConsoleStream out,
			MessageConsoleStream err) throws NotAllowedException
	{
		Map<IVdmSourceUnit, List<FmuAnnotation>> annotations = getSourceUnitAnnotations(project);

		Map<File, List<FmuAnnotation>> annotationsLexLinked = new HashMap<File, List<FmuAnnotation>>();

		for (Entry<IVdmSourceUnit, List<FmuAnnotation>> entry : annotations.entrySet())
		{
			annotationsLexLinked.put(entry.getKey().getSystemFile(), entry.getValue());
		}

		Map<PDefinition, FmuAnnotation> definitionAnnotation = new HashMap<PDefinition, FmuAnnotation>();

		for (SClassDefinition cDef : project.getModel().getClassList())
		{
			File file = cDef.getName().getLocation().getFile();
			if (annotationsLexLinked.containsKey(file))
			{
				// class contains a def thats annotated, now find it
				for (PDefinition mDef : cDef.getDefinitions())
				{
					if (mDef instanceof AValueDefinition
							|| mDef instanceof AInstanceVariableDefinition)
					{

						for (FmuAnnotation annotation : annotationsLexLinked.get(file))
						{
							if (annotation.tree.token.getLine() == mDef.getLocation().getStartLine() - 1)
							{
								String name = mDef instanceof AValueDefinition ? ((AValueDefinition) mDef).getPattern()
										+ ""
										: mDef.getName().getFullName();
								out.println(String.format("Found annotated definition '%s' with type '%s' and name '%s'", mDef.getLocation().getModule()
										+ "." + name, annotation.type, annotation.name));

								// FIXME: type chekc insuficient
								if (mDef.getType() instanceof SNumericBasicType
										|| mDef.getType() instanceof ABooleanBasicType)
								{
									definitionAnnotation.put(mDef, annotation);
								} else
								{
									err.println(String.format("Found annotated definition '%s' with type '%s' and name '%s'", mDef.getLocation().getModule()
											+ "." + name, annotation.type, annotation.name)
											+ " type not valid: "
											+ mDef.getType());
								}
								break;
							}
						}
					}

				}

			}
		}

		// Error reporting for unlinked definitions
		for (List<FmuAnnotation> annotationList : annotations.values())
		{
			for (FmuAnnotation commonTree : annotationList)
			{
				if (!definitionAnnotation.values().contains(commonTree))
				{

					for (Entry<IVdmSourceUnit, List<FmuAnnotation>> entry : annotations.entrySet())
					{
						if (entry.getValue().contains(commonTree))
						{
							IVdmSourceUnit unit = entry.getKey();
							FileUtility.addMarker(unit.getFile(), "Interface not linked to definition. The instanve-variable- or value- definition must be on the line below the annotation.", commonTree.tree.token.getLine() + 1, IMarker.SEVERITY_WARNING, IFmuExport.PLUGIN_ID);
							err.println(String.format("Unlinked interface annotation: file %s:line %d ", unit.getFile().getName(), commonTree.tree.getLine()));
						}
					}
				}
			}
		}
		return definitionAnnotation;
	}

	public Map<IVdmSourceUnit, List<FmuAnnotation>> getSourceUnitAnnotations(
			IVdmProject project)
	{

		Map<IVdmSourceUnit, List<FmuAnnotation>> annotations = new HashMap<IVdmSourceUnit, List<FmuAnnotation>>();
		try
		{
			for (IVdmSourceUnit unit : project.getSpecFiles())
			{
				FileUtility.deleteMarker(unit.getFile(), IMarker.PROBLEM, IFmuExport.PLUGIN_ID);
				AnnotationParserWrapper parser = new AnnotationParserWrapper();
				List<FmuAnnotation> result = parser.parse(unit.getSystemFile(), new RetainVdmCommentsFilter(unit.getFile().getContents(), "--@"));

				if (parser.hasErrors())
				{
					for (IError error : parser.getErrors())
					{
						FileUtility.addMarker(unit.getFile(), error.getMessage(), error.getLine() + 1, IMarker.SEVERITY_ERROR, IFmuExport.PLUGIN_ID);
					}
				}

				if (result != null && !result.isEmpty())
				{
					annotations.put(unit, result);
				}

			}
		} catch (CoreException e)
		{
			FmuExportPlugin.log("Error in annotation parsing", e);
		} catch (IOException e)
		{
			FmuExportPlugin.log("Error in annotation parsing", e);
		}

		return annotations;
	}

	private MessageConsole findConsole(String name)
	{
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		IConsole[] existing = conMan.getConsoles();
		for (int i = 0; i < existing.length; i++)
		{
			if (name.equals(existing[i].getName()))
			{
				return (MessageConsole) existing[i];
			}
		}
		// no console found, so create a new one
		MessageConsole myConsole = new MessageConsole(name, null);
		conMan.addConsoles(new IConsole[] { myConsole });
		return myConsole;
	}

}
