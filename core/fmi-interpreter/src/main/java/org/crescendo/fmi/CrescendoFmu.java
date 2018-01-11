/*
 * #%~
 * Fmi interface for the Crescendo Interpreter
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
import org.intocps.java.fmi.service.LogProtocolDriver;
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
import com.lausdahl.examples.Service.Fmi2LogReply;
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

public abstract class CrescendoFmu implements IServiceProtocol
{
	final static Logger logger = LoggerFactory.getLogger(CrescendoFmu.class);

	enum CrescendoStateType
	{
		None, Instantiated, Initialized, Stepping, Terminated
	}

	enum LogCategory
	{

		LogAll("logAll"), LogFmiCall("logFmiCall"), LogProtocol("Protocol"), LogVdmOut(
				"VdmOut"), LogVdmErr("VdmErr"), LogError("logError");

		final String name;

		LogCategory(String name)
		{
			this.name = name;
		}
	}

	public StateCache state;
	Double time = (double) 0;
	final String sessionName;
	CrescendoStateType protocolState = CrescendoStateType.None;
	private boolean loggingOn = false;

	double lastCommunicationPoint = 0;
	double lastStepSize = 0;

	private List<String> enabledLoggingCategories = new Vector<String>();
	private LogProtocolDriver logDriver = null;
	private boolean loggerConnected = false;

	static final Fmi2StatusReply ok = Fmi2StatusReply.newBuilder().setStatus(Fmi2StatusReply.Status.Ok).build();
	static final Fmi2StatusReply fatal = Fmi2StatusReply.newBuilder().setStatus(Fmi2StatusReply.Status.Fatal).build();
	static final Fmi2StatusReply error = Fmi2StatusReply.newBuilder().setStatus(Fmi2StatusReply.Status.Error).build();
	static final Fmi2StatusReply discard = Fmi2StatusReply.newBuilder().setStatus(Fmi2StatusReply.Status.Discard).build();

	/**
	 * Show the log message either in the console or sends through the log driver
	 * 
	 * @param message
	 */
	private void log(LogCategory category, Fmi2LogReply.Status status,
			String message)
	{
		logger.trace("Log message Category: {}, Status: {}, Message: {}", category.name, status, message);
		if (enabledLoggingCategories.contains(category.name))
		{
			if (logDriver != null && loggerConnected)
			{
				message = message.trim();
				if (message.endsWith("\n"))
				{
					message.substring(0, message.length() - 2);
				}
				logger.trace("Sending log message category: {}, data: {}", category.name, message);
				logDriver.log(category.name, status, message);
			} else
			{
				logger.warn("Log driver present: {}, is connected: {}, category: {}", logDriver != null, loggerConnected, category.name);
				System.out.println(message);
			}
		} else
		{
			logger.trace("Skipped log call back since category was not configured: {}", category.name);
		}
	}

	public void fmiLog(LogCategory category, String message)
	{

		if (loggingOn)
		{
			switch (category)
			{
				case LogVdmErr:
				case LogError:
					log(category, Fmi2LogReply.Status.Error, message);
					break;

				case LogAll:
				case LogFmiCall:
				case LogProtocol:
				case LogVdmOut:
				default:
					log(category, Fmi2LogReply.Status.Ok, message);
					break;
			}
		}

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
		fmiLog(LogCategory.LogError, string);
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

			fmiLog(LogCategory.LogProtocol, "DoStep called: " + nextFmiTime);

			if (nextFmiTime < time)
			{
				fmiLog(LogCategory.LogProtocol, "DoStep skipping execution next time is: "
						+ time);
				return ok;
			}

			long internalVdmClockTime = SystemClock.timeToInternal(TimeUnit.seconds, nextFmiTime);

			log(LogCategory.LogAll, Fmi2LogReply.Status.Ok, "DoStep VDM internal stop time: "
					+ internalVdmClockTime);
			List<NamedValue> res = null;

			try
			{

				res = FmiSimulationManager.getInstance().step(internalVdmClockTime, inputs);
			} catch (RemoteSimulationException e)
			{
//				if (e.getCause() != null)
//				{
//					fmiLog(LogCategory.LogVdmErr, e.getCause().getMessage());
//				}
				logger.warn("Error in doStep",e);
				fmiLog(LogCategory.LogError, e.getMessage());
				return fatal;
			}
			log(LogCategory.LogAll, Fmi2LogReply.Status.Ok, "DoStep VDM internal time reached: "
					+ internalVdmClockTime + " at doStep completion");
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
			log(LogCategory.LogAll, Fmi2LogReply.Status.Ok, "DoStep VDM clock conversion. Internal time: "
					+ internalVdmClockTime + " External [s]: "+curTime);

			// Write changes to the FMI cache
			state.syncOutputsToCache(res);

			time = curTime;
			fmiLog(LogCategory.LogProtocol, "DoStep waiting for next DoStep at: "
					+ time);

		} catch (Exception e)
		{
			logger.error("Error in doStep",e);
			fmiLog(LogCategory.LogError, "Error in DoStep: " + e.getMessage());
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply Terminate(Fmi2Empty parseFrom)
	{
		log(LogCategory.LogAll, Fmi2LogReply.Status.Ok, "terminate");
		try
		{
			FmiSimulationManager.getInstance().stopSimulation();
		} catch (RemoteSimulationException e)
		{
			e.printStackTrace();
			fmiLog(LogCategory.LogError, "Error in Terminate: "
					+ e.getMessage());
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply EnterInitializationMode(Fmi2Empty parseFrom)
	{
		loggerConnected = true;
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
			logger.warn("Error in initialization",e);
			fmiLog(LogCategory.LogError,  e.getMessage());
			return fatal;
		}
		log(LogCategory.LogAll, Fmi2LogReply.Status.Ok, "exit init");
		return ok;
	}

	@Override
	public Fmi2GetRealReply GetReal(Fmi2GetRequest request)
	{

		Fmi2GetRealReply.Builder reply = Fmi2GetRealReply.newBuilder();

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			long id = request.getValueReference(i);
			logger.trace("GetReal index: {}", id);
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
			logger.trace("GetBoolean index: {}", id);
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
			logger.trace("GetInteger index: {}", id);
			reply.addValues(state.integers[new Long(id).intValue()]);
		}
		return reply.build();

	}

	@Override
	public Fmi2GetStringReply GetString(Fmi2GetRequest request)
	{
		Fmi2GetStringReply.Builder reply = Fmi2GetStringReply.newBuilder();

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			long id = request.getValueReference(i);
			logger.trace("GetString index: {}", id);
			reply.addValues(state.strings[new Long(id).intValue()]);
		}
		reply.setValid(true);
		return reply.build();
	}

	/***
	 * Addition to INTO-CPS
	 */
	@Override
	public Fmi2GetMaxStepSizeReply GetMaxStepSize(Fmi2Empty parseFrom)
	{
		return Fmi2GetMaxStepSizeReply.newBuilder().setMaxStepSize(time==0?Double.MIN_VALUE:time
				- (lastCommunicationPoint + lastStepSize)).build();
	}

	@Override
	public Fmi2StatusReply Instantiate(Fmi2InstantiateRequest request)
	{
		if (!checkStats(CrescendoStateType.None))
		{
			return fatal;
		}

		if (request.getLogginOn())
		{
			loggingOn = request.getLogginOn();
			String callbackShmName = request.getCallbackShmName();
			logger.debug("Connecting callback log driver with shm key: '{}'", callbackShmName);
			try
			{
				logDriver = new LogProtocolDriver(callbackShmName);
			} catch (Throwable t)
			{
				logger.error("Faild to connect log protocol driver: {}", t.getMessage(), t);
			}
		}

		logger.debug(String.format("Instantiating %s.%s with loggingOn = %s, resource location='%s'", request.getFmuGuid(), request.getInstanceName(), request.getLogginOn()
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
					finalThis.fmiLog(LogCategory.LogVdmOut, message);
				}
			};
			Console.err = new StderrRedirector(new OutputStreamWriter(System.err, "UTF-8"))
			{
				@Override
				public void print(String message)
				{
					finalThis.fmiLog(LogCategory.LogVdmErr, message);
				}
			};

			VDMCO.replaceNewIdentifier.clear();

			Settings.prechecks = true;
			Settings.postchecks = true;
			Settings.invchecks = true;
			Settings.dynamictypechecks = true;
			Settings.measureChecks = true;
			boolean disableRtLog = true;
			boolean disableCoverage = false;
			boolean disableOptimization = false;

			Settings.usingCmdLine = true;
			Settings.usingDBGP = false;

			List<File> specfiles = new Vector<File>();

			File root = new File(new URI(request.getFmuResourceLocation()));
			File sourceRoot = new File(root, "model");
			log(LogCategory.LogAll, Fmi2LogReply.Status.Ok, "Source root: "
					+ sourceRoot);
			logger.trace("Source root: {}", sourceRoot);

			specfiles.addAll(FileUtils.listFiles(sourceRoot, new String[] { "vdmrt" }, true));
			logger.trace("Spec files: {}", org.apache.commons.lang3.StringUtils.join(specfiles, ","));

			File linkFile = new File(root, "modelDescription.xml".replace('/', File.separatorChar));
			File baseDirFile = new File(".");

			logger.trace("Model Description path: {}", linkFile);

			state = new StateCache(linkFile);

			FmiSimulationManager.getInstance().load(specfiles, state.links, new File("."), baseDirFile, disableRtLog, disableCoverage, disableOptimization);

			protocolState = CrescendoStateType.Instantiated;

		} catch (Exception e)
		{
			logger.error("Error in instantiate: " + e.getMessage(), e);
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply Reset(Fmi2Empty parseFrom)
	{
		fmiLog(LogCategory.LogProtocol, "Reset not supported");
		return error;
	}

	@Override
	public Fmi2StatusReply SetDebugLogging(Fmi2SetDebugLoggingRequest request)
	{
		loggingOn = request.getLoggingOn();
		for (int i = 0; i < request.getCatogoriesCount(); i++)
		{

			String category = request.getCatogories(i);
			logger.debug("Enabling logging for category: {}", category);
			enabledLoggingCategories.add(category);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetReal(final Fmi2SetRealRequest request)
	{
		if (!checkStats(CrescendoStateType.Instantiated, CrescendoStateType.Initialized, CrescendoStateType.Stepping))
		{
			return error;
		}

		Fmi2StatusReply status = ok;

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			int id = request.getValueReference(i);
			state.markParameterPending(id);
			logger.trace("Setting real[{}] = {}", id, request.getValues(i));

			if (Double.isNaN(request.getValues(i)))
			{
				status = discard;
				fmiLog(LogCategory.LogError, "Cannot set real with id " + id
						+ " invalid value: " + request.getValues(i));
			} else
			{
				state.reals[id] = request.getValues(i);
			}
		}
		return status;
	}

	@Override
	public Fmi2StatusReply SetInteger(Fmi2SetIntegerRequest request)
	{
		if (!checkStats(CrescendoStateType.Instantiated, CrescendoStateType.Initialized, CrescendoStateType.Stepping))
		{
			return error;
		}

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			int id = request.getValueReference(i);
			state.markParameterPending(id);
			state.integers[id] = request.getValues(i);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetBoolean(Fmi2SetBooleanRequest request)
	{
		if (!checkStats(CrescendoStateType.Instantiated, CrescendoStateType.Initialized, CrescendoStateType.Stepping))
		{
			return error;
		}

		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			int id = request.getValueReference(i);
			state.markParameterPending(id);
			state.booleans[id] = request.getValues(i);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetString(Fmi2SetStringRequest request)
	{
		if (!checkStats(CrescendoStateType.Instantiated, CrescendoStateType.Initialized, CrescendoStateType.Stepping))
		{
			return error;
		}
		for (int i = 0; i < request.getValueReferenceCount(); i++)
		{
			int id = request.getValueReference(i);
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
		fmiLog(LogCategory.LogProtocol, "Internal error: " + e.getMessage());
	}

	@Override
	public Fmi2StatusReply GetStatus(Fmi2StatusRequest request)
	{
		fmiLog(LogCategory.LogProtocol, "GetStatus not supported");
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

		fmiLog(LogCategory.LogProtocol, "GetRealStatus not supported");
		return null;
	}

	@Override
	public Fmi2IntegerStatusReply GetIntegerStatus(Fmi2StatusRequest request)
	{
		fmiLog(LogCategory.LogProtocol, "GetIntegerStatus not supported");
		return null;
	}

	@Override
	public Fmi2BooleanStatusReply GetBooleanStatus(Fmi2StatusRequest request)
	{
		fmiLog(LogCategory.LogProtocol, "GetBooleanStatus not supported");
		return null;
	}

	@Override
	public Fmi2StringStatusReply GetStringStatus(Fmi2StatusRequest request)
	{
		fmiLog(LogCategory.LogProtocol, "GetStringStatus not supported");
		return null;
	}

	@Override
	public void FreeInstantiate(Fmi2Empty arg0)
	{
		if (logDriver != null)
		{
			logger.debug("Closing log driver");
			loggerConnected = false;
			logDriver.close();
		}
		close();
	}

	public abstract void close();

}
