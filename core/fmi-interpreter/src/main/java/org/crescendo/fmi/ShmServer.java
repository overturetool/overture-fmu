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

import org.intocps.java.fmi.service.ProtocolDriver;
import org.intocps.java.fmi.shm.SharedMemory;
import org.intocps.java.fmi.shm.SharedMemoryServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShmServer
{

	final static Logger logger = LoggerFactory.getLogger(ShmServer.class);
	static ProtocolDriver driver = null;

	public static void main(String[] args) throws Exception
	{
		try
		{
			String memoryKey = SharedMemory.DEFAULT_MEMORY_NAME;// "shmFmiTest";// "OvertureFmiFileMappingObject";

			if (args.length > 1)
			{
				if (args[0].equals("-p") || args[1].equals("--port"))
				{

					memoryKey = args[1];
				}
			}

			logger.debug("Starting Crescendo ShmServer with key: '" + memoryKey
					+ "'");

			new SharedMemory().setDebug(logger.isDebugEnabled());
			SharedMemoryServer.setServerDebug(logger.isDebugEnabled());

			driver = new ProtocolDriver(memoryKey, new CrescendoFmu(memoryKey)
			{

				@Override
				public void close()
				{
					if (driver != null)
					{
						logger.debug("Stopping shared memory, and releasing associated resources");
						driver.close();
						logger.debug("Exiting shm server");
						System.exit(0);
					}
				}
			});

			driver.open();

			while (true)
			{
				Thread.sleep(30000);
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			throw e;
		}
	}

}
