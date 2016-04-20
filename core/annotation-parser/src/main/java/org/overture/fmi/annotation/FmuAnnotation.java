package org.overture.fmi.annotation;

import org.antlr.runtime.tree.CommonTree;

public class FmuAnnotation
{
	public final String type;
	public final String name;
	public final CommonTree tree;

	public FmuAnnotation(CommonTree tree, String type, String name)
	{
		this.tree = tree;
		this.type = type;
		this.name = name;

	}

	public CommonTree getTree()
	{
		return tree;
	}

	@Override
	public String toString()
	{
		return "Anotation name: '" + name + "' type: '" + type + "'";
	}
}
