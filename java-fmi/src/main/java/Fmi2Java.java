import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.destecs.core.vdmlink.LinkInfo;
import org.destecs.core.vdmlink.Links;
import org.destecs.protocol.exceptions.RemoteSimulationException;
import org.destecs.protocol.structs.StepStruct;
import org.destecs.protocol.structs.StepStructoutputsStruct;
import org.destecs.protocol.structs.StepinputsStructParam;
import org.destecs.vdm.SimulationManager;
import org.destecs.vdmj.VDMCO;
import org.overture.config.Settings;
import org.overture.interpreter.scheduler.SystemClock;
import org.overture.interpreter.scheduler.SystemClock.TimeUnit;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Fmi2Java
{
	public static class NamedNodeMapIterator implements Iterator<Node>,
			Iterable<Node>
	{
		private final NamedNodeMap list;
		private int index = 0;

		public NamedNodeMapIterator(NamedNodeMap list)
		{
			this.list = list;
		}

		// @Override
		public boolean hasNext()
		{
			return list != null && index < list.getLength();
		}

		// @Override
		public Node next()
		{
			return list.item(index++);
		}

		// @Override
		public void remove()
		{
			throw new RuntimeException("Not implemented");
		}

		// @Override
		public Iterator<Node> iterator()
		{
			return this;
		}

	}

	public static class NodeIterator implements Iterator<Node>, Iterable<Node>
	{
		private final NodeList list;
		private int index = 0;

		public NodeIterator(NodeList list)
		{
			this.list = list;
		}

		// @Override
		public boolean hasNext()
		{
			return index < list.getLength();
		}

		// @Override
		public Node next()
		{
			return list.item(index++);
		}

		// @Override
		public void remove()
		{
			throw new RuntimeException("Not implemented");
		}

		// @Override
		public Iterator<Node> iterator()
		{
			return this;
		}

	}

	private static class FmiState
	{
		final int size = 20;
		public final double[] reals = new double[size];
		public final int[] integers = new int[size];
		public final boolean[] booleans = new boolean[size];
	}

	static FmiState state;
	static Links links;

	static Double time = (double) 0;

	private static List<StepinputsStructParam> collectInputsFromCache()
	{
		List<StepinputsStructParam> inputs = new Vector<StepinputsStructParam>();

		for (Entry<String, LinkInfo> entry : links.getInputs().entrySet())
		{

			Double value = 0.0;
			switch (((ExtendedLinkInfo) entry.getValue()).type)
			{
				case Boolean:
					value = state.booleans[Integer.valueOf(entry.getKey())] ? 1.0
							: 0.0;
					break;
				case Integer:
					value = new Double(state.integers[Integer.valueOf(entry.getKey())]);
					break;
				case Real:
					value = state.reals[Integer.valueOf(entry.getKey())];
					break;
				case String:
					break;
				default:
					break;

			}

			inputs.add(new StepinputsStructParam(entry.getKey(), Arrays.asList(new Double[] { value }), Arrays.asList(new Integer[] { 1 })));
		}

		return inputs;
	}

	public static void doStep(double t, double h)
	{
		// System.out.println("doStep in external java");

		try
		{
			List<StepinputsStructParam> inputs = collectInputsFromCache();

			double timeTmp = new Double(SystemClock.timeToInternal(TimeUnit.seconds, t
					+ h));
			StepStruct res = SimulationManager.getInstance().step(timeTmp, inputs, new Vector<String>());

			res.time = SystemClock.internalToTime(TimeUnit.seconds, res.time.longValue());
			syncOutputsToCache(res.outputs);

			time = res.time;
		} catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static void syncOutputsToCache(List<StepStructoutputsStruct> outputs)
	{
		for (StepStructoutputsStruct output : outputs)
		{
			// System.out.println(String.format("\tOutput %s = %s", output.name, output.value.get(0)));
			ExtendedLinkInfo link = (ExtendedLinkInfo) links.getLinks().get(output.name);

			int index = Integer.valueOf(output.name);
			switch (link.type)
			{
				case Boolean:
					state.booleans[index] = output.value.get(0) > 0;
					break;
				case Integer:
					state.integers[index] = output.value.get(0).intValue();
					break;
				case Real:
					state.reals[index] = output.value.get(0);
					break;
				case String:
					break;
				default:
					break;

			}

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
			
			for (Entry<String, LinkInfo> link : links.getSharedDesignParameters().entrySet())
			{
				int index = Integer.valueOf(link.getKey());
				
				ExtendedLinkInfo info = (ExtendedLinkInfo) link.getValue();
				
				Double value = 0.0;
				switch (info.type)
				{
					case Boolean:
						value = state.booleans[index] ? 1.0
								: 0.0;
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
		} catch (RemoteSimulationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("exit init");
		return 0;
	}

	public static byte freeInstance()
	{
		System.out.println("free instance");
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
		// state.reals[minLevelId] = 1.0;
		// state.reals[maxLevelId] = 3.0;
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

			File linkFile = new File(sourceRoot, "modelDescription.xml".replace('/', File.separatorChar));
			File baseDirFile = new File(".");

			links = createVdmLinks(linkFile);

			SimulationManager.getInstance().load(specfiles, links, new File("."), baseDirFile, disableRtLog, disableCoverage, disableOptimization);

		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	static class ExtendedLinkInfo extends LinkInfo
	{
		public enum Type
		{
			Real, Boolean, Integer, String
		}

		public final Type type;

		public ExtendedLinkInfo(String identifier, List<String> qualifiedName,
				int line, Type type)
		{
			super(identifier, qualifiedName, line);
			this.type = type;
		}

	}

	static Links createVdmLinks(File linkFile) throws SAXException,
			IOException, ParserConfigurationException,
			XPathExpressionException, DOMException
	{
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		Document doc = docBuilderFactory.newDocumentBuilder().parse(linkFile);

		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();

		final Map<String, LinkInfo> link = new HashMap<String, LinkInfo>();
		final List<String> outputs = new Vector<String>();
		final List<String> inputs = new Vector<String>();
		final List<String> designParameters = new Vector<String>();

		for (Node n : new NodeIterator(lookup(doc, xpath, "//ScalarVariable")))
		{
			NamedNodeMap attributes = n.getAttributes();

			String name = attributes.getNamedItem("name").getNodeValue();
			String valRef = attributes.getNamedItem("valueReference").getNodeValue();

			List<String> qualifiedName = Arrays.asList(name.split("\\."));
			ExtendedLinkInfo.Type type = ExtendedLinkInfo.Type.Real;

			for (@SuppressWarnings("unused") Node n1 : new NodeIterator(lookup(n, xpath, "Real")))
			{
				type = ExtendedLinkInfo.Type.Real;
			}

			for (@SuppressWarnings("unused") Node n1 : new NodeIterator(lookup(n, xpath, "Boolean")))
			{
				type = ExtendedLinkInfo.Type.Boolean;
			}

			for (@SuppressWarnings("unused") Node n1 : new NodeIterator(lookup(n, xpath, "Integer")))
			{
				type = ExtendedLinkInfo.Type.Integer;
			}

			link.put(valRef, new ExtendedLinkInfo(valRef, qualifiedName, 0, type));

			String causality = attributes.getNamedItem("causality").getNodeValue();

			if ("output".equals(causality))
			{
				outputs.add(valRef);
			} else if ("input".equals(causality))
			{
				inputs.add(valRef);
			} else if ("parameter".equals(causality))
			{
				designParameters.add(valRef);
			}

		}

		return new Links(link, outputs, inputs, new Vector<String>(), designParameters, new Vector<String>());
	}

	final static boolean DEBUG = false;

	static NodeList lookup(Object doc, XPath xpath, String expression)
			throws XPathExpressionException
	{
		XPathExpression expr = xpath.compile(expression);

		if (DEBUG)
		{
			// System.out.println("Starting from: " + formateNodeWithAtt(doc));
		}
		final NodeList list = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

		if (DEBUG)
		{
			System.out.print("\tFound: ");
		}
		boolean first = true;
		for (@SuppressWarnings("unused") Node n : new NodeIterator(list))
		{
			if (DEBUG)
			{
				// System.out.println((!first ? "\t       " : "")
				// + formateNodeWithAtt(n));
			}
			first = false;
		}
		if (first)
		{
			if (DEBUG)
			{
				System.out.println("none");
			}
		}
		return list;

	}

	public static byte reset()
	{
		System.out.println("reset");
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
		System.out.println("terminate");
		try
		{
			SimulationManager.getInstance().stopSimulation();
		} catch (RemoteSimulationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	public static void test(String guid, String instanceNAme, boolean b)
	{
		System.out.println(String.format("Instantiating %s.%s with loggingOn = %s", guid, instanceNAme, ""
				+ ""));
	}

}
