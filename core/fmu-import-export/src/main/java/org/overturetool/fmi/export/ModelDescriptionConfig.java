package org.overturetool.fmi.export;

import java.util.List;
import java.util.Vector;

public class ModelDescriptionConfig
{
	boolean canBeInstantiatedOnlyOncePerProcess;
	boolean needsExecutionTool;
	final List<String> sourceFiles = new Vector<>();
	String fmuGUID;
}
