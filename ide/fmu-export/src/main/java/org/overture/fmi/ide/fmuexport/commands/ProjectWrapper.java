package org.overture.fmi.ide.fmuexport.commands;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Vector;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Shell;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.lex.Dialect;
import org.overture.fmi.ide.fmuexport.FmuExportPlugin;
import org.overture.fmi.ide.fmuexport.IFmuExport;
import org.overture.ide.core.IVdmModel;
import org.overture.ide.core.ast.NotAllowedException;
import org.overture.ide.core.resources.IVdmProject;
import org.overture.ide.core.resources.IVdmSourceUnit;
import org.overture.ide.core.utility.FileUtility;
import org.overture.ide.ui.utility.VdmTypeCheckerUi;

public class ProjectWrapper implements org.overturetool.fmi.IProject
{

	final IProject project;

	final IVdmProject vdmProject;
	final private Shell shell;

	private File tempFolder = null;
	private List<? extends SClassDefinition> classes;

	public ProjectWrapper(Shell shell, IProject project, IVdmProject vdmProject)
	{
		super();
		this.shell = shell;
		this.project = project;
		this.vdmProject = vdmProject;
	}

	@Override
	public void addMarkser(File unit, String message, int line, MarkerType error)
	{
		for (IVdmSourceUnit su : vdmProject.getModel().getSourceUnits())
		{
			if (su.getSystemFile().equals(unit))
			{
				FileUtility.addMarker(su.getFile(), message, line, IMarker.SEVERITY_WARNING, IFmuExport.PLUGIN_ID);
			}
		}
	}

	@Override
	public void copyResourcesToTempFolder(final String resourceFolder)
	{
		IContainer lib = vdmProject.getModelBuildPath().getLibrary();

		if (lib.exists())
		{
			try
			{
				lib.accept(new IResourceVisitor()
				{
					@Override
					public boolean visit(IResource resource)
							throws CoreException
					{
						if (resource.getType() == IResource.FOLDER)
						{
							return true;
						} else if (resource.getType() == IResource.FILE
								&& "jar".equalsIgnoreCase(resource.getFileExtension()))
						{

							if (resource instanceof IFile)
							{
								try
								{
									createProjectTempRelativeFile(resourceFolder+"/"+resource.getName(), ((IFile) resource).getContents());
								} catch (IOException e)
								{
									log(e);
								}
							}
						}
						return false;
					}
				});
			} catch (CoreException e)
			{
				log(e);
			}
		}

	}

	@Override
	public void createProjectTempRelativeFile(String path, InputStream content)
			throws IOException
	{
		File file = new File(getTempFolder(), path.replace('/', File.separatorChar));
		try
		{
			IOUtils.copy(content, FileUtils.openOutputStream(file));
		} catch (IOException e)
		{
			log(e);
		}
	}

	@Override
	public void createSpecFileProjectRelative(String path, InputStream content)
			throws IOException
	{
		File file = new File(getSourceRootPath(), path.replace('/', File.separatorChar));
		try
		{
			IOUtils.copy(content, FileUtils.openOutputStream(file));
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (IOException e)
		{
			log(e);
		} catch (CoreException e)
		{
			log(e);
		}
	}

	@Override
	public void deleteMarker(File unit)
	{
		for (IVdmSourceUnit su : vdmProject.getModel().getSourceUnits())
		{
			if (su.getSystemFile().equals(unit))
			{
				FileUtility.deleteMarker(su.getFile(), IMarker.PROBLEM, IFmuExport.PLUGIN_ID);
			}
		}
	}

	@Override
	public List<? extends SClassDefinition> getClasses()
	{
		return classes;
	}


	@Override
	public String getName()
	{
		return project.getName();
	}

	@Override
	public File getOutputFolder()
	{
		return vdmProject.getModelBuildPath().getOutput().getLocation().toFile();
	}

	@Override
	public File getSourceRootPath()
	{
		return vdmProject.getModelBuildPath().getModelSrcPaths().get(0).getLocation().toFile();
	}

	@Override
	public List<File> getSpecFiles()
	{
		List<File> specFiles = new Vector<File>();

		for (IVdmSourceUnit su : vdmProject.getModel().getSourceUnits())
		{
			specFiles.add(su.getFile().getLocation().toFile());
		}
		return specFiles;
	}

	@Override
	public File getTempFolder() throws IOException
	{
		if (tempFolder == null)
		{
			tempFolder = Files.createTempDirectory("overture-fmu").toFile();
		}
		return tempFolder;
	}

	@Override
	public void log(Exception exception)
	{
		FmuExportPlugin.log(exception);
	}

	@Override
	public void log(String message, Exception exception)
	{
		FmuExportPlugin.log(message, exception);
	}

	@Override
	public void scheduleJob(final IJob j)
	{
		Job job = new Job("Compressing FMU")
		{

			@Override
			protected IStatus run(IProgressMonitor monitor)
			{
				try
				{
					monitor.beginTask("Compressing", IProgressMonitor.UNKNOWN);
					j.run();
					monitor.done();
				} catch (Exception e)
				{
					return new Status(Status.ERROR, IFmuExport.PLUGIN_ID, "Error compressing fmu", e);
				}
				return Status.OK_STATUS;
			}
		};

		job.schedule(0);
	}

	@Override
	public boolean typeCheck()
	{
		// if (specFiles.isEmpty())
		// {
		this.classes = new Vector<SClassDefinition>();
		// return true;
		// }

		final IVdmModel model = vdmProject.getModel();
		if (model.isParseCorrect())
		{

			if (model == null || !model.isTypeCorrect())
			{
				VdmTypeCheckerUi.typeCheck(shell, vdmProject);
			}

			if (model.isTypeCorrect()
					&& vdmProject.getDialect() == Dialect.VDM_RT)
			{

				try
				{
					this.classes = model.getClassList();
				} catch (NotAllowedException e)
				{
					log(e);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void cleanUp() throws IOException
	{
		if (tempFolder != null)
		{
			FileUtils.deleteDirectory(tempFolder);
		}
	}

	@Override
	public boolean isOutputDebugEnabled()
	{
		return false;
	}
}
