package org.crescendo.fmi;

import java.util.List;
import java.util.Vector;

import org.destecs.protocol.exceptions.RemoteSimulationException;
import org.destecs.vdm.SimulationManager;
import org.destecs.vdm.utility.SeqValueInfo;
import org.destecs.vdm.utility.VDMClassHelper;
import org.destecs.vdm.utility.ValueInfo;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.intf.lex.ILexNameToken;
import org.overture.interpreter.runtime.ValueException;
import org.overture.interpreter.runtime.state.ASystemClassDefinitionRuntime;
import org.overture.interpreter.values.CPUValue;
import org.overture.interpreter.values.NameValuePairList;
import org.overture.interpreter.values.RealValue;
import org.overture.interpreter.values.UndefinedValue;
import org.overture.interpreter.values.Value;

public class FmiSimulationManager extends SimulationManager
{


	/**
	 * @return The unique instance of this class.
	 */
	static public FmiSimulationManager getInstance()
	{
		if (null == _instance)
		{
			_instance = new FmiSimulationManager();
		}
		return (FmiSimulationManager) _instance;
	}

	/**
	 * FMI step method using basic named values
	 * 
	 * @param outputTime
	 * @param inputs
	 * @param events
	 * @return
	 * @throws RemoteSimulationException
	 */
	public synchronized List<NamedValue> step(Double outputTime,
			List<NamedValue> inputs) throws RemoteSimulationException
	{
		checkMainContext();

		for (NamedValue p : inputs)
		{
			setScalarValue(p.name,getValue(p.name),  p.value );
		}

		doInternalStep(outputTime, null);// no events

		List<NamedValue> outputs = new Vector<NamedValue>();
		outputs.add(new NamedValue("time", new RealValue(nextSchedulableActionTime), -1));

		for (String key : links.getOutputs().keySet())
		{
			try
			{
				NamedValue value = getSimpleOutput(key);
				if (value != null)
				{
					outputs.add(value);
				} else
				{
					throw new RemoteSimulationException("Faild to get output parameter, output not bound for: "
							+ key);
				}
			} catch (ValueException e)
			{
				debugErr(e);
				throw new RemoteSimulationException("Faild to get output parameter", e);
			}
		}

		return outputs;
	}
	
	ValueInfo unwrapSeqInfoValue(ValueInfo value)
	{
		if(value==null)
		{
			return null;
		}else if(value instanceof SeqValueInfo)
		{
			return new ValueInfo(value.name, value.classDef, ((SeqValueInfo) value).source,
					value.cpu);
		}
		return value;
	}
	
	@Override
	protected ValueInfo getValue(String name) throws RemoteSimulationException
	{
		return unwrapSeqInfoValue(super.getValue(name));
	}

	private NamedValue getSimpleOutput(String name) throws ValueException,
			RemoteSimulationException
	{
		NameValuePairList list = ASystemClassDefinitionRuntime.getSystemMembers();
		if (list != null && links.getLinks().containsKey(name))
		{
			List<String> varName = links.getQualifiedName(name);

			Value value = VDMClassHelper.digForVariable(varName.subList(1, varName.size()), list).value;

			if (value.deref() instanceof UndefinedValue)
			{
				throw new RemoteSimulationException("Value: " + name
						+ " not initialized");
			}

			return new NamedValue(name, value, -1);

		}
		throw new RemoteSimulationException("Value: " + name + " not found");
	}
}
