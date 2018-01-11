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

import org.intocps.java.fmi.shm.SharedMemory;
import org.intocps.java.fmi.shm.SharedMemoryServer;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lausdahl.examples.Service.Fmi2DoStepRequest;
import com.lausdahl.examples.Service.Fmi2Empty;
import com.lausdahl.examples.Service.Fmi2InstantiateRequest;
import com.lausdahl.examples.Service.Fmi2SetStringRequest;
import com.lausdahl.examples.Service.Fmi2StatusReply;

public class SimulationTest
{

	Fmi2Empty empty = Fmi2Empty.newBuilder().build();

	@Test
	public void test()
	{
		Logger logger = LoggerFactory.getLogger(ShmServer.class);
		new SharedMemory().setDebug(logger.isDebugEnabled());
		SharedMemoryServer.setServerDebug(logger.isDebugEnabled());

		CrescendoFmu fmu = new CrescendoFmu("test-in-out-session-test")
		{

			@Override
			public void close()
			{

			}
		};

		String resourcePath = new File(".").toURI().resolve("src/test/resources/var-transfer-in-out-test/").toString();
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.Instantiate(Fmi2InstantiateRequest.newBuilder().setFmuResourceLocation(resourcePath).build()).getStatus());

		final int b_in = 1;
		final int b_out = 2;

		final int i_in = 0;
		final int i_out = 3;

		final int r_in = 5;
		final int r_out = 4;

		final int s_in = 7;
		final int s_out = 6;

		final int p_s = 8;

		final int p2_out = 10;
		// final int p2_s=11;
		final int p_out = 9;

		// do step 1.
		fmu.state.booleans[b_in] = true;
		fmu.state.integers[i_in] = 9;
		fmu.state.reals[r_in] = 9.9;
		fmu.state.strings[s_in] = "my value";

		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.SetString(Fmi2SetStringRequest.newBuilder().addValueReference(p_s).addValues("parameter").build()).getStatus());

		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.EnterInitializationMode(empty).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.ExitInitializationMode(empty).getStatus());

		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.DoStep(Fmi2DoStepRequest.newBuilder().setCurrentCommunicationPoint(0).setCommunicationStepSize(4).build()).getStatus());

		Assert.assertEquals(true, fmu.state.booleans[b_out]);
		Assert.assertEquals(9, fmu.state.integers[i_out]);
		Assert.assertEquals(9.9, fmu.state.reals[r_out], 0.001);
		Assert.assertEquals("my value", fmu.state.strings[s_out]);
		Assert.assertEquals("parameter", fmu.state.strings[p_out]);
		Assert.assertEquals("p2-default", fmu.state.strings[p2_out]);

		// do step 2.
		fmu.state.booleans[b_in] = false;
		fmu.state.integers[i_in] = 10;
		fmu.state.reals[r_in] = 10.10;
		fmu.state.strings[s_in] = "my value 2";

		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.DoStep(Fmi2DoStepRequest.newBuilder().setCurrentCommunicationPoint(4).setCommunicationStepSize(4).build()).getStatus());

		Assert.assertEquals(false, fmu.state.booleans[b_out]);
		Assert.assertEquals(10, fmu.state.integers[i_out]);
		Assert.assertEquals(10.10, fmu.state.reals[r_out], 0.001);
		Assert.assertEquals("my value 2", fmu.state.strings[s_out]);

	}

	@Test
	public void getMaxStepSizeTest()
	{
		Logger logger = LoggerFactory.getLogger(ShmServer.class);
		new SharedMemory().setDebug(logger.isDebugEnabled());
		SharedMemoryServer.setServerDebug(logger.isDebugEnabled());

		CrescendoFmu fmu = new CrescendoFmu("max-step-size-test")
		{

			@Override
			public void close()
			{

			}
		};

		String resourcePath = new File(".").toURI().resolve("src/test/resources/skip-maxstepsize-test/").toString();
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.Instantiate(Fmi2InstantiateRequest.newBuilder().setFmuResourceLocation(resourcePath).build()).getStatus());

		final int p_s = 8;

		// do step 1.
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.EnterInitializationMode(empty).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.ExitInitializationMode(empty).getStatus());
		double curTime = 0;
		double maxStepSize = fmu.GetMaxStepSize(null).getMaxStepSize();
		System.out.println("Cur time: " + curTime + " - maxStepSize: " + maxStepSize);
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.DoStep(Fmi2DoStepRequest.newBuilder().setCurrentCommunicationPoint(0).setCommunicationStepSize(maxStepSize).build()).getStatus());
		curTime = maxStepSize;

		// do step 2 till 10
		for (int i = 0; i < 9; i++) {
			maxStepSize = fmu.GetMaxStepSize(null).getMaxStepSize();
			System.out.println("Cur time: " + curTime + " - maxStepSize: " + maxStepSize);
			// The model contains periodic(10E6,0,0,0) and cycles(0) which means that it should sync every 10th millisecond or 0.01 second.
			// The value must be > 0.009 and < 0.011.
			// This discrepancy is due to double interpretation. Really the difference is around 0.000000000000000005
			Assert.assertTrue("getMaxStepSize is not between 0.009 and 0.011", maxStepSize > 0.009 && maxStepSize < 0.011);
			Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.DoStep(Fmi2DoStepRequest.newBuilder().setCurrentCommunicationPoint(curTime).setCommunicationStepSize(maxStepSize).build()).getStatus());
			curTime = curTime + maxStepSize;
		}
	}

	@Test
	public void testInitError()
	{
		Logger logger = LoggerFactory.getLogger(ShmServer.class);
		new SharedMemory().setDebug(logger.isDebugEnabled());
		SharedMemoryServer.setServerDebug(logger.isDebugEnabled());

		CrescendoFmu fmu = new CrescendoFmu("test-in-out-session-test")
		{

			@Override
			public void close()
			{

			}
		};

		String resourcePath = new File(".").toURI().resolve("src/test/resources/init-error/").toString();
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.Instantiate(Fmi2InstantiateRequest.newBuilder().setFmuResourceLocation(resourcePath).setLogginOn(true).build()).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.EnterInitializationMode(empty).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Fatal, fmu.ExitInitializationMode(empty).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Fatal, fmu.DoStep(Fmi2DoStepRequest.newBuilder().setCurrentCommunicationPoint(0).setCommunicationStepSize(4).build()).getStatus());
	}

	@Test
	public void testDtcError()
	{
		Logger logger = LoggerFactory.getLogger(ShmServer.class);
		new SharedMemory().setDebug(logger.isDebugEnabled());
		SharedMemoryServer.setServerDebug(logger.isDebugEnabled());

		CrescendoFmu fmu = new CrescendoFmu("test-in-out-session-test")
		{

			@Override
			public void close()
			{

			}
		};

		String resourcePath = new File(".").toURI().resolve("src/test/resources/dtc-error/").toString();
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.Instantiate(Fmi2InstantiateRequest.newBuilder().setFmuResourceLocation(resourcePath).setLogginOn(true).build()).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.EnterInitializationMode(empty).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.ExitInitializationMode(empty).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Fatal, fmu.DoStep(Fmi2DoStepRequest.newBuilder().setCurrentCommunicationPoint(0).setCommunicationStepSize(4).build()).getStatus());
	}
}
