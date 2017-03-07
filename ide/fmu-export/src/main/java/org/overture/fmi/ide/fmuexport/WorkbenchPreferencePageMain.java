package org.overture.fmi.ide.fmuexport;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.overture.ide.ui.VdmUIPlugin;

public class WorkbenchPreferencePageMain  extends FieldEditorPreferencePage implements
IWorkbenchPreferencePage {

	@Override
	protected void createFieldEditors()
	{
		addField(new BooleanFieldEditor(IFmuExport.ENABLE_TRACABILITY, "Enable tracability", getFieldEditorParent()));
	}
	
	@Override
	protected IPreferenceStore doGetPreferenceStore()
	{
		return VdmUIPlugin.getDefault().getPreferenceStore();
	}
	
	@Override
	protected void performDefaults()
	{
		IPreferenceStore store = getPreferenceStore();
		store.setDefault(IFmuExport.ENABLE_TRACABILITY, true);
		super.performDefaults();
	}

	public void init(IWorkbench workbench)
	{
		IPreferenceStore store = getPreferenceStore();
		store.setDefault(IFmuExport.ENABLE_TRACABILITY, true);
	}
}

