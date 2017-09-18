/*
 * #%~
 * Fmu import exporter
 * %%
 * Copyright (C) 2015 - 2017 Overture
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #~%
 */
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
	
	boolean isTracabilityEnabled();
	
	String getToolDebugConfig();
}
