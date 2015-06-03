import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.destecs.protocol.exceptions.RemoteSimulationException;
import org.destecs.protocol.structs.StepStruct;
import org.destecs.protocol.structs.StepStructoutputsStruct;
import org.destecs.protocol.structs.StepinputsStructParam;
import org.destecs.vdm.SimulationManager;
import org.destecs.vdmj.VDMCO;
import org.overture.config.Settings;
import org.overture.interpreter.scheduler.SystemClock;
import org.overture.interpreter.scheduler.SystemClock.TimeUnit;

public class Fmi2Java
{
	private static class FmiState
	{
		final int size = 20;
		public final double[] reals = new double[size];
		@SuppressWarnings("unused")
		public final int[] integers = new int[size];
		public final boolean[] booleans = new boolean[size];
	}

	static FmiState state;

	static Double time = (double) 0;

	static final int levelId = 3;

	static final int valveId = 4;

	static final int maxLevelId = 0;

	static final int minLevelId = 1;

	public static void doStep(double t, double h)
	{
		System.out.println("doStep in external java");

		try
		{
			// System.out.println(InterpreterUtil.interpret("1+1"));
			List<StepinputsStructParam> inputs = new Vector<StepinputsStructParam>();
			inputs.add(new StepinputsStructParam("level", Arrays.asList(new Double[] { state.reals[levelId] }), Arrays.asList(new Integer[] { 1 })));

			double timeTmp = new Double(SystemClock.timeToInternal(TimeUnit.seconds, t
					+ h));
			StepStruct res = SimulationManager.getInstance().step(timeTmp, inputs, new Vector<String>());

			res.time = SystemClock.internalToTime(TimeUnit.seconds, res.time.longValue());
			// System.levelSensor.level
			// System.valveActuator.valveState

			System.out.println(String.format("Running from: %.6f to %.6f", time, res.time));
			for (StepStructoutputsStruct output : res.outputs)
			{
				System.out.println(String.format("\tOutput %s = %s", output.name, output.value.get(0)));
				if (output.name.equals("valve"))
				{
					state.booleans[valveId] = output.value.get(0) > 0;
				}
			}

			time = res.time;
		} catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static byte enterInitializationMode()
	{
		System.out.println("enter init");
		return 0;
	}

	public static byte exitInitializationMode()
	{
		try
		{
			// set sdp
			List<Map<String, Object>> parameters = new Vector<Map<String, Object>>();

			Map<String, Object> pMax = new HashMap<String, Object>();
			pMax.put("name", "maxlevel");
			pMax.put("value", new Double[] { state.reals[maxLevelId] });
			pMax.put("size", new Integer[] { 1 });
			parameters.add(pMax);

			Map<String, Object> pMin = new HashMap<String, Object>();
			pMin.put("name", "minlevel");
			pMin.put("value", new Double[] { state.reals[minLevelId] });
			pMin.put("size", new Integer[] { 1 });
			parameters.add(pMin);

			SimulationManager.getInstance().setDesignParameters(parameters);

			// start
			SimulationManager.getInstance().start(time.longValue());
		} catch (RemoteSimulationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("enter init");
		return 0;
	}

	public static byte freeInstance()
	{
		System.out.println("enter init");
		return 0;
	}

	public static byte getBoolean(long[] vr, boolean[] values)
	{
		for (int i = 0; i < vr.length; i++)
		{
			long id = vr[i];
			values[i] = state.booleans[new Long(id).intValue()];
		}
		return 0;
	}

	public static byte getReal(long[] vr, double[] values)
	{
		for (int i = 0; i < vr.length; i++)
		{
			long id = vr[i];
			values[i] = state.reals[new Long(id).intValue()];
		}
		return 0;
	}

	public static void instantiate(String guid, String instanceName,
			String resourceFolder, boolean loggingOn)
	{
		System.out.println(String.format("Instantiating %s.%s with loggingOn = %s, resource location='%s'", guid, instanceName, loggingOn
				+ "", resourceFolder));
		Fmi2Java.state = new FmiState();
		// init default state
		state.reals[minLevelId] = 1.0;
		state.reals[maxLevelId] = 3.0;
		try
		{
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

			File root = new File(resourceFolder).getParentFile();
			File sourceRoot = new File(root, "sources");
			System.out.println("Source root: " + sourceRoot);

			for (File file : sourceRoot.listFiles())
			{
				if (file.getName().endsWith(".vdmrt"))
				{
					specfiles.add(file);
				}
			}

			File linkFile = new File(sourceRoot, "vdm.link".replace('/', File.separatorChar));
			File baseDirFile = new File(".");

			SimulationManager.getInstance().load(specfiles, linkFile, new File("."), baseDirFile, disableRtLog, disableCoverage, disableOptimization);

		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static byte reset()
	{
		System.out.println("enter init");
		return 0;
	}

	public static byte setBoolean(long[] vr, boolean[] values)
	{
		for (int i = 0; i < vr.length; i++)
		{
			long id = vr[i];
			state.booleans[new Long(id).intValue()] = values[i];
		}
		return 0;
	}

	public static byte setReal(long[] vr, double[] values)
	{
		for (int i = 0; i < vr.length; i++)
		{
			long id = vr[i];
			state.reals[new Long(id).intValue()] = values[i];
		}
		return 0;
	}

	public static byte terminate()
	{
		System.out.println("enter init");
		return 0;
	}

	public static void test(String guid, String instanceNAme, boolean b)
	{
		System.out.println(String.format("Instantiating %s.%s with loggingOn = %s", guid, instanceNAme, ""
				+ ""));
	}

}
