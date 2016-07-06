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
