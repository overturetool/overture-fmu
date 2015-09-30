package org.crescendo.fmi;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import org.destecs.core.vdmlink.LinkInfo;
import org.destecs.protocol.exceptions.RemoteSimulationException;
import org.destecs.protocol.structs.StepStruct;
import org.destecs.protocol.structs.StepinputsStructParam;
import org.destecs.vdm.SimulationManager;
import org.destecs.vdmj.VDMCO;
import org.intocps.java.fmi.service.IServiceProtocol;
import org.overture.config.Settings;
import org.overture.interpreter.scheduler.SystemClock;
import org.overture.interpreter.scheduler.SystemClock.TimeUnit;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.lausdahl.examples.Service.DoStepRequest;
import com.lausdahl.examples.Service.Empty;
import com.lausdahl.examples.Service.Fmi2StatusReply;
import com.lausdahl.examples.Service.Fmi2StatusReply.Status;
import com.lausdahl.examples.Service.GetBooleanReply;
import com.lausdahl.examples.Service.GetBooleanReply.Builder;
import com.lausdahl.examples.Service.GetIntegerReply;
import com.lausdahl.examples.Service.GetMaxStepSizeReply;
import com.lausdahl.examples.Service.GetRealReply;
import com.lausdahl.examples.Service.GetRequest;
import com.lausdahl.examples.Service.InstantiateRequest;
import com.lausdahl.examples.Service.SetBooleanRequest;
import com.lausdahl.examples.Service.SetDebugLoggingRequest;
import com.lausdahl.examples.Service.SetIntegerRequest;
import com.lausdahl.examples.Service.SetRealRequest;
import com.lausdahl.examples.Service.SetStringRequest;
import com.lausdahl.examples.Service.SetupExperimentRequest;

public class CrescendoGrpcFmu implements IServiceProtocol {

	StateCache state;
	Double time = (double) 0;

	static final Fmi2StatusReply ok = Fmi2StatusReply.newBuilder().setStatus(Status.Ok).build();
	static final Fmi2StatusReply fatal = Fmi2StatusReply.newBuilder().setStatus(Status.Fatal).build();
	static final Fmi2StatusReply error = Fmi2StatusReply.newBuilder().setStatus(Status.Error).build();

	@Override
	public void error(String string) {
		System.err.println(string);
	}

	@Override
	public Fmi2StatusReply DoStep(DoStepRequest request) {
		// TODO Auto-generated method stub

		// System.out.println("doStep in external java");

		try {
			List<StepinputsStructParam> inputs = state.collectInputsFromCache();

			double timeTmp = new Double(SystemClock.timeToInternal(TimeUnit.seconds,
					request.getCurrentCommunicationPoint() + request.getCommunicationStepSize()));
			StepStruct res = SimulationManager.getInstance().step(timeTmp, inputs, new Vector<String>());

			res.time = SystemClock.internalToTime(TimeUnit.seconds, res.time.longValue());
			state.syncOutputsToCache(res.outputs);

			time = res.time;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply Terminate(Empty parseFrom) {
		System.out.println("terminate");
		try {
			SimulationManager.getInstance().stopSimulation();
		} catch (RemoteSimulationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply EnterInitializationMode(Empty parseFrom) {
		return ok;
	}

	@Override
	public Fmi2StatusReply ExitInitializationMode(Empty parseFrom) {
		try {
			// set sdp
			List<Map<String, Object>> parameters = new Vector<Map<String, Object>>();

			for (Entry<String, LinkInfo> link : state.links.getSharedDesignParameters().entrySet()) {
				int index = Integer.valueOf(link.getKey());

				ExtendedLinkInfo info = (ExtendedLinkInfo) link.getValue();

				Double value = 0.0;
				switch (info.type) {
				case Boolean:
					value = state.booleans[index] ? 1.0 : 0.0;
					break;
				case Integer:
					value = new Double(state.integers[index]);
					break;
				case Real:
					value = state.reals[index];
					break;
				case String:
					break;
				default:
					break;

				}

				Map<String, Object> pMax = new HashMap<String, Object>();
				pMax.put("name", link.getKey());
				pMax.put("value", new Double[] { value });
				pMax.put("size", new Integer[] { 1 });
				parameters.add(pMax);
			}

			SimulationManager.getInstance().setDesignParameters(parameters);

			// start
			SimulationManager.getInstance().start(time.longValue());
		} catch (RemoteSimulationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return fatal;
		}
		System.out.println("exit init");
		return ok;
	}

	@Override
	public GetBooleanReply GetBoolean(GetRequest request) {
		Builder reply = GetBooleanReply.newBuilder();

		for (int i = 0; i < request.getValueReferenceCount(); i++) {
			long id = request.getValueReference(i);
			reply.addValues(state.booleans[new Long(id).intValue()]);
			// values[i] = state.booleans[new Long(id).intValue()];
		}
		return (reply.build());
	}

	@Override
	public GetIntegerReply GetInteger(GetRequest parseFrom) {
		// TODO Auto-generated method stub
		return GetIntegerReply.newBuilder().build();
	}

	@Override
	public GetMaxStepSizeReply GetMaxStepSize(Empty parseFrom) {
		return (GetMaxStepSizeReply.newBuilder().setMaxStepSize(Double.MAX_VALUE).build());

	}

	@Override
	public GetRealReply GetReal(GetRequest request) {
		com.lausdahl.examples.Service.GetRealReply.Builder reply = GetRealReply.newBuilder();

		for (int i = 0; i < request.getValueReferenceCount(); i++) {
			long id = request.getValueReference(i);
			// values[i] = state.reals[new Long(id).intValue()];
			reply.addValues(state.reals[new Long(id).intValue()]);
		}
		return (reply.build());
	}

	@Override
	public GeneratedMessage GetString(GetRequest parseFrom) {
		return error;
	}

	@Override
	public Fmi2StatusReply Instantiate(InstantiateRequest request) {
		System.out.println(
				String.format("Instantiating %s.%s with loggingOn = %s, resource location='%s'", request.getFmuGuid(),
						request.getInstanceName(), request.getLogginOn() + "", request.getFmuResourceLocation()));
		// default state
		// state.reals[minLevelId] = 1.0;
		// state.reals[maxLevelId] = 3.0;
		try {
			SimulationManager.getInstance().initialize();

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

			File root = new File(request.getFmuResourceLocation()).getParentFile();
			// File root = new
			// File("/home/parallels/Desktop/vdm-tankcontroller");
			File sourceRoot = new File(root, "sources");
			System.out.println("Source root: " + sourceRoot);

			for (File file : sourceRoot.listFiles()) {
				if (file.getName().endsWith(".vdmrt")) {
					specfiles.add(file);
				}
			}

			File linkFile = new File(sourceRoot, "modelDescription.xml".replace('/', File.separatorChar));
			File baseDirFile = new File(".");

			state = new StateCache(linkFile);

			SimulationManager.getInstance().load(specfiles, state.links, new File("."), baseDirFile, disableRtLog,
					disableCoverage, disableOptimization);

		} catch (Exception e) {
			e.printStackTrace();
			// TODO
			return fatal;
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply Reset(Empty parseFrom) {
		return error;
	}

	@Override
	public Fmi2StatusReply SetBoolean(SetBooleanRequest request) {
		for (int i = 0; i < request.getValueReferenceCount(); i++) {
			long id = request.getValueReference(i);
			state.booleans[new Long(id).intValue()] = request.getValues(i);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetDebugLogging(SetDebugLoggingRequest parseFrom) {
		return ok;
	}

	@Override
	public Fmi2StatusReply SetInteger(SetIntegerRequest parseFrom) {
		// TODO Auto-generated method stub
		return error;
	}

	@Override
	public Fmi2StatusReply SetReal(SetRealRequest request) {
		for (int i = 0; i < request.getValueReferenceCount(); i++) {
			long id = request.getValueReference(i);
			state.reals[new Long(id).intValue()] = request.getValues(i);
		}
		return ok;
	}

	@Override
	public Fmi2StatusReply SetString(SetStringRequest parseFrom) {
		// TODO Auto-generated method stub
		return error;
	}

	@Override
	public Fmi2StatusReply SetupExperiment(SetupExperimentRequest parseFrom) {
		return ok;
	}

	@Override
	public void error(InvalidProtocolBufferException e) {
		e.printStackTrace();
	}

}
