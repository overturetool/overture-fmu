package org.crescendo.fmi;

import org.intocps.java.fmi.service.ProtocolDriver;
import org.intocps.java.fmi.shm.SharedMemory;
import org.intocps.java.fmi.shm.SharedMemoryServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShmServer
{

	final static Logger logger = LoggerFactory.getLogger(ShmServer.class);

	public static void main(String[] args) throws Exception
	{

		String memoryKey = SharedMemory.DEFAULT_MEMORY_NAME;// "shmFmiTest";// "OvertureFmiFileMappingObject";

		if (args.length > 1)
		{
			if (args[0].equals("-p") || args[1].equals("--port"))
			{

				memoryKey = (args[1]);
			}
		}

		logger.debug("Starting Crescendo ShmServer with key: '"
				+ memoryKey + "'");

		SharedMemory.setDebug(logger.isDebugEnabled());
		SharedMemoryServer.setServerDebug(logger.isDebugEnabled());

		ProtocolDriver driver = new ProtocolDriver(memoryKey, new CrescendoFmu(memoryKey));

		driver.start();

		while (true)
			Thread.sleep(30000);

	}

}
