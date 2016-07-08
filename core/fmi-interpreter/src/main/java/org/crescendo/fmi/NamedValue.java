package org.crescendo.fmi;

import org.overture.interpreter.values.Value;

public class NamedValue
{
	public final Value value;
	public final String name;
	public final long id;

	public NamedValue(String name, Value value, long id)
	{
		super();
		this.value = value;
		this.name = name;
		this.id = id;
	}

	@Override
	public String toString()
	{
		return name + ": '" + value + "'";
	}

}
