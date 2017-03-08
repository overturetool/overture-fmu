/*
 * #%~
 * UML2 Translator
 * %%
 * Copyright (C) 2008 - 2014 Overture
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
package org.overture.fmi.ide.fmuexport;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class FmuExportPlugin extends AbstractUIPlugin
{

	// The plug-in ID
	public static final String PLUGIN_ID = IFmuExport.PLUGIN_ID;

	// The shared instance
	private static FmuExportPlugin plugin;

	/**
	 * The constructor
	 */
	public FmuExportPlugin()
	{
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception
	{
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception
	{
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static FmuExportPlugin getDefault()
	{
		return plugin;
	}

	public static void log(Exception exception)
	{
		getDefault().getLog().log(new Status(IStatus.ERROR, IFmuExport.PLUGIN_ID, "FMUExportPlugin", exception));
	}

	public static void log(String message, Exception exception)
	{
		getDefault().getLog().log(new Status(IStatus.ERROR, IFmuExport.PLUGIN_ID, message, exception));
	}
	
	/**
	 * Initializes a preference store with default preference values for this plug-in.
	 */
	@Override
	protected void initializeDefaultPreferences(IPreferenceStore store)
	{
		initializeDefaultMainPreferences(store);
	}

	public static void initializeDefaultMainPreferences(IPreferenceStore store)
	{
		store.setDefault(IFmuExport.ENABLE_TRACABILITY, true);
		
	}

}
