package org.crescendo.fmi;

import org.intocps.java.fmi.service.ProtocolDriver;
import org.intocps.java.fmi.shm.SharedMemory;

public class ShmServer {


	public static void main(String[] args) throws Exception {

		String memoryKey =SharedMemory.DEFAULT_MEMORY_NAME;//"shmFmiTest";// "OvertureFmiFileMappingObject";

		if (args.length > 1) {
			if (args[0].equals("-p") || args[1].equals("--port")) {
			
				memoryKey = (args[1]);
			}
		}

		System.out.println("Starting Crescendo ShmServer with key: '"+memoryKey+"'");
	

		ProtocolDriver driver = new ProtocolDriver(memoryKey, new CrescendoFmu(memoryKey));

		driver.start();

		while (true)
			Thread.sleep(30000);

	}

}
