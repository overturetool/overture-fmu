/*
 * #%~
 * VDM Reflection Library
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
import org.overture.ast.analysis.AnalysisException;
import org.overture.interpreter.runtime.Interpreter;
import org.overture.interpreter.runtime.ValueException;
import org.overture.interpreter.values.BooleanValue;
import org.overture.interpreter.values.NameValuePair;
import org.overture.interpreter.values.ObjectValue;
import org.overture.interpreter.values.Value;

public class Reflect
{
	public static Value setMember(Value objectValue, Value nameValue,
			Value newValue) throws ValueException, AnalysisException
	{
		String name = IO.stringOf(nameValue);

		ObjectValue obj = (ObjectValue) objectValue.deref();

		boolean found = false;
		for (NameValuePair member : obj.members.asList())
		{
			if (name.equals(member.name.getName()))
			{
				member.value.set(member.name.getLocation(), newValue, Interpreter.getInstance().initialContext);
				found = true;
				break;
			}
		}

		if (!found)
		{
			throw new ValueException(-1, String.format("Unable to find member '%s' of object '%s'", name, objectValue.toString()), Interpreter.getInstance().initialContext);
		}

		return new BooleanValue(true);

	}
}
