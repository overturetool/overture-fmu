import java.io.File;
import java.util.Arrays;
import java.util.List;
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
	public static void instantiate(String guid, String instanceName,String resourceFolder,
			boolean loggingOn)
	{

		System.out.println(String.format("Instantiating %s.%s with loggingOn = %s, resource location='%s'", guid, instanceName, loggingOn
				+ "",resourceFolder));
		
		
		try{
		
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
		File sourceRoot = new File(root,"sources");
		System.out.println("Source root: "+sourceRoot);
		
		for (File file : sourceRoot.listFiles())
		{
			if(file.getName().endsWith(".vdmrt"))
				specfiles.add(file);
		}
		
		File linkFile = new File(sourceRoot,"vdm.link".replace('/', File.separatorChar));
		File baseDirFile = new File(".");

		SimulationManager.getInstance().load(specfiles, linkFile, new File("."), baseDirFile, disableRtLog, disableCoverage, disableOptimization);
		
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}

	public static void test(String guid, String instanceNAme, boolean b)
	{

		System.out.println(String.format("Instantiating %s.%s with loggingOn = %s", guid, instanceNAme, ""
				+ ""));
	}

	public static byte enterInitializationMode()
	{
		System.out.println("enter init");
		return 0;
	}
 static Double time = (double) 0;
	public static byte exitInitializationMode()
	{
		
		try
		{
			SimulationManager.getInstance().start(time.longValue());
		} catch (RemoteSimulationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("enter init");
		return 0;
	}

	public static byte terminate()
	{
		System.out.println("enter init");
		return 0;
	}

	public static byte reset()
	{
		System.out.println("enter init");
		return 0;
	}

	public static byte freeInstance()
	{
		System.out.println("enter init");
		return 0;
	}

	public static byte setReal(long[] vr, double[] values)
	{
//		System.out.println("Setting VR:" + vr.length);
		for (int i = 0; i < vr.length; i++)
		{
			long id = vr[i];
			if(id==3)
			{
				level = values[i];
				System.out.println("-- Level just updated to "+level);
			}
//			System.out.println(String.format("vr %d = %.2f", vr[i], values[i]));
		}
//		System.out.flush();
		return 0;
	}

	public static byte getReal(long[] vr, double[] values)
	{
//		System.out.println("Getting VR:" + vr.length);
		for (int i = 0; i < vr.length; i++)
		{
			values[i] = i + 0.1;
			System.out.println(String.format("vr %d = %.2f", vr[i], values[i]));
		}
//		setReal(vr, values);
//		System.out.flush();
		return 0;
	}
	
	
	public static byte setBoolean(long[] vr, boolean[] values)
	{
//		System.out.println("Setting VR:" + vr.length);
		for (int i = 0; i < vr.length; i++)
		{
//			System.out.println(String.format("vr %d = %.2f", vr[i], values[i]));
			System.out.println("vr "+vr[i]+" = "+ values[i]);
		}
//		System.out.flush();
		return 0;
	}
static boolean valve;
	public static byte getBoolean(long[] vr, boolean[] values)
	{
		
		
//		System.out.println("Getting VR:" + vr.length);
		for (int i = 0; i < vr.length; i++)
		{
			long id = vr[i];
			if(id==4)
			{
				values[i] = valve;	
			}else{
			
//			System.out.println("vr "+vr[i]+" = true");
			values[i] = true;
		}}
//		setBoolean(vr, values);
//		System.out.flush();
		return 0;
	}
	static Double level = 10.0;
	public static void doStep(double t, double h)
	{
		System.out.println("doStep in external java");
		
		try
		{
//			System.out.println(InterpreterUtil.interpret("1+1"));
			List<StepinputsStructParam> inputs = new Vector<StepinputsStructParam>();
			inputs.add(new StepinputsStructParam("level", Arrays.asList(new Double[]{level}),Arrays.asList(new Integer[]{1})));

			double timeTmp = new Double(SystemClock.timeToInternal(TimeUnit.seconds, t+h));
			StepStruct res = SimulationManager.getInstance().step(timeTmp, inputs, new Vector<String>());

			res.time = SystemClock.internalToTime(TimeUnit.seconds, res.time.longValue());
//			System.levelSensor.level
//			System.valveActuator.valveState
			
		
			System.out.println(String.format("Running from: %.6f to %.6f",time,res.time));
			for (StepStructoutputsStruct output : res.outputs)
			{
				System.out.println(String.format("\tOutput %s = %s",output.name,output.value.get(0)));
				if(output.name.equals("valve"))
				{
					valve = output.value.get(0)>0;
				}
			}
			level-=1;
			
			time =res.time;
		} catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		System.out.flush();
	}
	

}
