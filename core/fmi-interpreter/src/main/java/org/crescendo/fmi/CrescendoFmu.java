package org.crescendo.fmi;

import java.io.File;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;

import org.apache.commons.io.FileUtils;
import org.destecs.core.vdmlink.LinkInfo;
import org.destecs.protocol.exceptions.RemoteSimulationException;
import org.destecs.vdmj.VDMCO;
import org.intocps.java.fmi.service.IServiceProtocol;
import org.overture.config.Settings;
import org.overture.interpreter.messages.Console;
import org.overture.interpreter.messages.StderrRedirector;
import org.overture.interpreter.messages.StdoutRedirector;
import org.overture.interpreter.scheduler.SystemClock;
import org.overture.interpreter.scheduler.SystemClock.TimeUnit;
import org.overture.interpreter.values.BooleanValue;
import org.overture.interpreter.values.IntegerValue;
import org.overture.interpreter.values.RealValue;
import org.overture.interpreter.values.SeqValue;
import org.overture.interpreter.values.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.lausdahl.examples.Service.Fmi2BooleanStatusReply;
import com.lausdahl.examples.Service.Fmi2DoStepRequest;
import com.lausdahl.examples.Service.Fmi2Empty;
import com.lausdahl.examples.Service.Fmi2GetBooleanReply;
import com.lausdahl.examples.Service.Fmi2GetIntegerReply;
import com.lausdahl.examples.Service.Fmi2GetMaxStepSizeReply;
import com.lausdahl.examples.Service.Fmi2GetRealReply;
import com.lausdahl.examples.Service.Fmi2GetRequest;
import com.lausdahl.examples.Service.Fmi2GetStringReply;
import com.lausdahl.examples.Service.Fmi2InstantiateRequest;
import com.lausdahl.examples.Service.Fmi2IntegerStatusReply;
import com.lausdahl.examples.Service.Fmi2RealStatusReply;
import com.lausdahl.examples.Service.Fmi2SetBooleanRequest;
import com.lausdahl.examples.Service.Fmi2SetDebugLoggingRequest;
import com.lausdahl.examples.Service.Fmi2SetIntegerRequest;
import com.lausdahl.examples.Service.Fmi2SetRealRequest;
import com.lausdahl.examples.Service.Fmi2SetStringRequest;
import com.lausdahl.examples.Service.Fmi2SetupExperimentRequest;
import com.lausdahl.examples.Service.Fmi2StatusReply;
import com.lausdahl.examples.Service.Fmi2StatusRequest;
import com.lausdahl.examples.Service.Fmi2StringStatusReply;

public class CrescendoFmu implements IServiceProtocol
{
	final static Logger logger = LoggerFactory.getLogger(CrescendoFmu.class);

	enum CrescendoStateType
	{
		None, Instantiated, Initialized, Stepping, Terminated
	}

	public StateCache state;
	Double time = (double) 0;
	final String sessionName;
	CrescendoStateType protocolState = CrescendoStateType.None;
	private boolean loggingOn = true;
	
	double lastCommunicationPoint = 0;
	double lastStepSize = 0;
	
	private List<String> enabledLoggingCategories = new Vector<String>();

	static final Fmi2StatusReply ok = Fmi2StatusReply.newBuilder().setStatus(Fmi2StatusReply.Status.Ok).build();
	static final Fmi2StatusReply fatal = Fmi2StatusReply.newBuilder().setStatus(Fmi2StatusReply.Status.Fatal).build();
	static final Fmi2StatusReply error = Fmi2StatusReply.newBuilder().setStatus(Fmi2StatusReply.Status.Error).build();
	static final Fmi2StatusReply discard = Fmi2StatusReply.newBuilder().setStatus(Fmi2StatusReply.Status.Discard).build();

	enum FmiLogCategory
	{
		Protocol, VdmOut, VdmErr, Error
	}

	public void fmiLog(FmiLogCategory category, String message)
	{

		if (loggingOn)
		{
			switch (category)
			{
				case Error:
					System.err.println(message);
					break;
				case Protocol:
				case VdmErr:
				case VdmOut:
					System.out.println(message);
				default:
					break;
			}
		}

		// TODO: redirect to protocol
	}

	public CrescendoFmu(String sessionName)
	{
		this.sessionName = sessionName;
	}

	boolean checkStats(CrescendoStateType... st)
	{
		for (CrescendoStateType crescendoStateType : st)
		{
			if (crescendoStateType == protocolState)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void error(String string)
	{
		System.err.println(string);
	}

	@Override
	public Fmi2StatusReply DoStep(Fmi2DoStepRequest request)
	{

		if (!checkStats(CrescendoStateType.Initialized))
		{
			return fatal;
		}

		try
		{
			List<NamedValue> inputs = state.collectInputsFromCache();

			double nextFmiTime = request.getCurrentCommunicationPoint()
					+ request.getCommunicationStepSize();
			
			this.lastCommunicationPoint = request.getCurrentCommunicationPoint();
			this.lastStepSize = request.getCommunicationStepSize();

			fmiLog(FmiLogCategory.Protocol, "DoStep called: " + nextFmiTime);

			if (nextFmiTime < time)
			{
				fmiLog(FmiLogCategory.Protocol, "DoStep skipping execution next time is: "
						+ time);
				return ok;
			}

			double internalVdmClockTime = new Double(SystemClock.timeToInternal(TimeUnit.seconds, nextFmiTime));

			System.out.println("DoStem VDM time: " + internalVdmClockTime);
			List<NamedValue> res = FmiSimulationManager.getInstance().step(internalVdmClockTime, inputs);

			NamedValue timeValue = null;
			for (NamedValue namedValue : res)
			{
				if (namedValue.name.equals("time"))
				{
					timeValue = namedValue;
				}
			}
			res.remove(timeValue);

			Double curTime = timeValue.value.realValue(null);

			// Convert back to SI from internal VDM clock
			curTime = SystemClock.internalToTime(TimeUnit.seconds, curTime.longValue());

			// Write changes to the FMI cache
			state.syncOutputsToCache(res);

			time = curTime;
			fmiLog(FmiLogCategory.Protocol, "DoStep waiting for next DoStep at: "
					+ time);

		} catch (Exception e)
		{
			e.printStackTrace();
			fmiLog(FmiLogCategory.Error, "Error in DoStep: " + e.getMessage());
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply Terminate(Fmi2Empty parseFrom)
	{
		System.out.println("terminate");
		try
		{
			FmiSimulationManager.getInstance().stopSimulation();
		} catch (RemoteSimulationException e)
		{
			e.printStackTrace();
			fmiLog(FmiLogCategory.Error, "Error in Terminate: "
					+ e.getMessage());
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply EnterInitializationMode(Fmi2Empty parseFrom)
	{
		return ok;
	}

	@Override
	public Fmi2StatusReply ExitInitializationMode(Fmi2Empty parseFrom)
	{
		if (!checkStats(CrescendoStateType.Instantiated))
		{
			return fatal;
		}
		try
		{

			for (Entry<String, LinkInfo> link : state.getPendingSetParameters().entrySet())
			{
				int index = Integer.valueOf(link.getKey());

				ExtendedLinkInfo info = (ExtendedLinkInfo) link.getValue();

				Value value = null;
				switch (info.type)
				{
					case Boolean:
						value = new BooleanValue(state.booleans[index]);
						break;
					case Integer:
						value = new IntegerValue(state.integers[index]);
						break;
					case Real:
						try
						{
							value = new RealValue(state.reals[index]);
						} catch (Exception e)
						{
							return fatal;
						}
						break;
					case String:
						value = new SeqValue(state.strings[index]);
						break;
					default:
						break;

				}

				logger.debug("Added sdp with name: '{}' value: '{}' valueref: '{}'", state.links.getQualifiedName(link.getKey()), value, link.getKey());
				FmiSimulationManager.getInstance().setParameter(new NamedValue(link.getKey(), value, -1));
			}

			logger.debug("Starting simulation manager with time: {}", time.longValue());
			// start
			FmiSimulationManager.getInstance().start(time.longValue());

			protocolState = CrescendoStateType.Initialized;
		} catch (RemoteSimulationException e)
		{
			e.printStackTrace();
			fmiLog(FmiLogCategory.Error, "Error in ExitInitializationMode (setDesignParameters, start): "
					+ e.getMessage());
			return fatal;
		}
		System.out.println("exit init");
		return ok;
	}

	@Override
	public Fmi2GetRealReply GetReal(Fmi2GetRequest request)
	{

		Fmi2GetRealReply.Builder reply = Fmi2GetRealReply.newBuilder();

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			long id = request.getValueReference(i);
			reply.addValues(state.reals[new Long(id).intValue()]);
		}
		return reply.build();
	}

	@Override
	public Fmi2GetBooleanReply GetBoolean(Fmi2GetRequest request)
	{

		Fmi2GetBooleanReply.Builder reply = Fmi2GetBooleanReply.newBuilder();

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			long id = request.getValueReference(i);
			reply.addValues(state.booleans[new Long(id).intValue()]);
		}
		return reply.build();
	}

	@Override
	public Fmi2GetIntegerReply GetInteger(Fmi2GetRequest request)
	{

		Fmi2GetIntegerReply.Builder reply = Fmi2GetIntegerReply.newBuilder();

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			long id = request.getValueReference(i);
			reply.addValues(state.integers[new Long(id).intValue()]);
		}
		return reply.build();

	}

	@Override
	public GeneratedMessage GetString(Fmi2GetRequest request)
	{
		Fmi2GetStringReply.Builder reply = Fmi2GetStringReply.newBuilder();

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			long id = request.getValueReference(i);
			reply.addValues(state.strings[new Long(id).intValue()]);
		}
		return reply.build();
	}

	/***
	 * Addition to INTO-CPS
	 */
	@Override
	public Fmi2GetMaxStepSizeReply GetMaxStepSize(Fmi2Empty parseFrom)
	{
		return Fmi2GetMaxStepSizeReply.newBuilder().setMaxStepSize(time-(lastCommunicationPoint+lastStepSize)).build();
	}

	@Override
	public Fmi2StatusReply Instantiate(Fmi2InstantiateRequest request)
	{
		if (!checkStats(CrescendoStateType.None))
		{
			return fatal;
		}
		System.out.println(String.format("Instantiating %s.%s with loggingOn = %s, resource location='%s'", request.getFmuGuid(), request.getInstanceName(), request.getLogginOn()
				+ "", request.getFmuResourceLocation()));
		try
		{
			FmiSimulationManager.getInstance().initialize();

			final CrescendoFmu finalThis = this;

			Console.out = new StdoutRedirector(new OutputStreamWriter(System.out, "UTF-8"))
			{
				@Override
				public void print(String message)
				{
					finalThis.fmiLog(FmiLogCategory.VdmErr, message);
				}
			};
			Console.err = new StderrRedirector(new OutputStreamWriter(System.err, "UTF-8"))
			{
				@Override
				public void print(String message)
				{
					finalThis.fmiLog(FmiLogCategory.VdmOut, message);
				}
			};

			VDMCO.replaceNewIdentifier.clear();

			Settings.prechecks = true;
			Settings.postchecks = true;
			Settings.invchecks = true;
			Settings.dynamictypechecks = true;
			Settings.measureChecks = true;
			boolean disableRtLog = false;
			boolean disableCoverage = false;
			boolean disableOptimization = false;

			Settings.usingCmdLine = true;
			Settings.usingDBGP = false;

			List<File> specfiles = new Vector<File>();

			File root = new File(new URI(request.getFmuResourceLocation()));
			File sourceRoot = new File(root, "model");
			System.out.println("Source root: " + sourceRoot);

			specfiles.addAll(FileUtils.listFiles(sourceRoot, new String[] { "vdmrt" }, true));

			File linkFile = new File(root, "modelDescription.xml".replace('/', File.separatorChar));
			File baseDirFile = new File(".");

			state = new StateCache(linkFile);

			FmiSimulationManager.getInstance().load(specfiles, state.links, new File("."), baseDirFile, disableRtLog, disableCoverage, disableOptimization);

			protocolState = CrescendoStateType.Instantiated;

		} catch (Exception e)
		{
			e.printStackTrace();
			fmiLog(FmiLogCategory.Error, "Error in instantiate: "
					+ e.getMessage());
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply Reset(Fmi2Empty parseFrom)
	{
		fmiLog(FmiLogCategory.Protocol, "Reset not supported");
		return error;
	}

	@Override
	public Fmi2StatusReply SetDebugLogging(Fmi2SetDebugLoggingRequest request)
	{
		loggingOn = request.getLoggingOn();
		for (int i = 0; i < request.getCatogoriesCount(); i++)
		{
			enabledLoggingCategories.add(request.getCatogories(i));
		}
		return ok;
	}

//	interface GetFunction<T>
//	{
//		T get(int id);
//	}
//
//	private <T> Fmi2StatusReply genericSet(T[] array, int size,
//			GetFunction<Integer> getIdFun, GetFunction<T> getValueFun)
//	{
//		boolean notInitialized = checkStats(CrescendoStateType.None, CrescendoStateType.Instantiated);
//
//		for (int i = 0; i < size; i++)
//		{
//			int id = getIdFun.get(i);
//			if (notInitialized)
//				state.markParameterPending(id);
//			T value = getValueFun.get(i);
//			logger.trace("Setting real[{}] = {}", id, value);
//			array[(int) id] = value;
//		}
//		return ok;
//	}

	@Override
	public Fmi2StatusReply SetReal(final Fmi2SetRealRequest request)
	{
				
		boolean notInitialized = checkStats(CrescendoStateType.None, CrescendoStateType.Instantiated);

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			int id = request.getValueReference(i);
			if (notInitialized)
				state.markParameterPending(id);
			logger.trace("Setting real[{}] = {}", id, request.getValues(i));
			state.reals[id] = request.getValues(i);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetInteger(Fmi2SetIntegerRequest request)
	{
		boolean notInitialized = checkStats(CrescendoStateType.None, CrescendoStateType.Instantiated);
		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			int id = request.getValueReference(i);
			if (notInitialized)
				state.markParameterPending(id);
			state.integers[id] = request.getValues(i);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetBoolean(Fmi2SetBooleanRequest request)
	{boolean notInitialized = checkStats(CrescendoStateType.None, CrescendoStateType.Instantiated);
		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			int id = request.getValueReference(i);
			if (notInitialized)
				state.markParameterPending(id);
			state.booleans[id] = request.getValues(i);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetString(Fmi2SetStringRequest request)
	{boolean notInitialized = checkStats(CrescendoStateType.None, CrescendoStateType.Instantiated);
		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			int id = request.getValueReference(i);
			if (notInitialized)
				state.markParameterPending(id);
			state.strings[id] = request.getValues(i);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetupExperiment(Fmi2SetupExperimentRequest parseFrom)
	{
		return ok;
	}

	@Override
	public void error(InvalidProtocolBufferException e)
	{
		e.printStackTrace();
		fmiLog(FmiLogCategory.Protocol, "Internal error: " + e.getMessage());
	}

	@Override
	public Fmi2StatusReply GetStatus(Fmi2StatusRequest request)
	{
		// TODO Auto-generated method stub
		fmiLog(FmiLogCategory.Protocol, "GetStatus not supported");
		return discard;
	}

	@Override
	public Fmi2RealStatusReply GetRealStatus(Fmi2StatusRequest request)
	{

		switch (request.getStatus())
		{
			case UNRECOGNIZED:
				break;
			case fmi2DoStepStatus:
				break;
			case fmi2LastSuccessfulTime:
				return Fmi2RealStatusReply.newBuilder().setValue(time).build();
			case fmi2PendingStatus:
				break;
			case fmi2Terminated:
				break;
			default:
				break;

		}

		// TODO Auto-generated method stub
		fmiLog(FmiLogCategory.Protocol, "GetRealStatus not supported");
		return null;
	}

	@Override
	public Fmi2IntegerStatusReply GetIntegerStatus(Fmi2StatusRequest request)
	{
		// TODO Auto-generated method stub
		fmiLog(FmiLogCategory.Protocol, "GetIntegerStatus not supported");
		return null;
	}

	@Override
	public Fmi2BooleanStatusReply GetBooleanStatus(Fmi2StatusRequest request)
	{
		// TODO Auto-generated method stub
		fmiLog(FmiLogCategory.Protocol, "GetBooleanStatus not supported");
		return null;
	}

	@Override
	public Fmi2StringStatusReply GetStringStatus(Fmi2StatusRequest request)
	{
		// TODO Auto-generated method stub
		fmiLog(FmiLogCategory.Protocol, "GetStringStatus not supported");
		return null;
	}

}
