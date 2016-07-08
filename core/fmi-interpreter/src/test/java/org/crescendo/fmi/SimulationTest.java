package org.crescendo.fmi;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

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

		CrescendoFmu fmu = new CrescendoFmu("test-in-out-session-test");

		String resourcePath = "src/test/resources/var-transfer-in-out-test/resources".replace('/', File.separatorChar);
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

		// do step 1.
		fmu.state.booleans[b_in] = true;
		fmu.state.integers[i_in] = 9;
		fmu.state.reals[r_in] = 9.9;
		fmu.state.strings[s_in] = "my value";

		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.SetString(Fmi2SetStringRequest.newBuilder().addValueReference( p_s).addValues( "parameter").build()).getStatus());
		
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.EnterInitializationMode(empty).getStatus());
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.ExitInitializationMode(empty).getStatus());

		
		Assert.assertEquals(Fmi2StatusReply.Status.Ok, fmu.DoStep(Fmi2DoStepRequest.newBuilder().setCurrentCommunicationPoint(0).setCommunicationStepSize(4).build()).getStatus());

		Assert.assertEquals(true, fmu.state.booleans[b_out]);
		Assert.assertEquals(9, fmu.state.integers[i_out]);
		Assert.assertEquals(9.9, fmu.state.reals[r_out], 0.001);
		Assert.assertEquals("my value", fmu.state.strings[s_out]);

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
}
