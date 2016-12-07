package org.overturetool.fmi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.overture.ast.definitions.SClassDefinition;

public interface IProject
{
	String getName();

	File getSourceRootPath();

	boolean typeCheck();

	List<? extends SClassDefinition> getClasses();

	void createProjectTempRelativeFile(String path, InputStream content)
			throws IOException;

	void createSpecFileProjectRelative(String path, InputStream content)
			throws IOException;

	void log(Exception exception);

	void log(String message, Exception exception);

	List<File> getSpecFiles();

	public static enum MarkerType
	{
		Error, Warning
	}

	void deleteMarker(File unit);

	void addMarkser(File unit, String message, int line, MarkerType error);

	File getOutputFolder();

	public interface IJob
	{
		void run();
	}

	void scheduleJob(IJob job);

	void copyResourcesToTempFolder(String resourcesFolder);

	File getTempFolder() throws IOException;

	void cleanUp() throws IOException;

	boolean isOutputDebugEnabled();
	
	String getToolDebugConfig();
}
