/*
 * watertankFmu.cpp
 *
 *  Created on: Mar 3, 2016
 *      Author: kel
 */
#include "Fmu.h"

//#GENERATED_DEFINES

extern "C"
{
#include "Fmu.h"
#include "Vdm.h"
#ifndef PERIODIC_GENERATED
#include "LevelSensor.h"
#include "ValveActuator.h"
#include "Controller.h"
#include <unistd.h>

#include "System.h"
#include "World.h"
#include <pthread.h>
#endif

//#GENERATED_MODEL_INCLUDE
}

#ifndef PERIODIC_GENERATED
// FMI variable reference mapping
#define MIN_LEVEL_ID 1
#define MAX_LEVEL_ID 0

#define VALVE_ID 4
#define LEVEL_ID 3
#endif

TVP sys = NULL;

void replaceTvp(TVP* val,TVP newVal)
{
	TVP tmp = *val;
	*val = newVal;
	vdmFree(tmp);
}

//#GENERATED_INSERT

#ifndef PERIODIC_GENERATED

void syncInputsToModel()
{
	SET_FIELD(HardwareInterface, HardwareInterface, g_System_hwi, level, newReal(fmiBuffer.realBuffer[LEVEL_ID]));

	replaceTvp(&g_HardwareInterface_minlevel, newReal(fmiBuffer.realBuffer[MIN_LEVEL_ID]));
	replaceTvp(&g_HardwareInterface_maxlevel, newReal(fmiBuffer.realBuffer[MAX_LEVEL_ID]));

}

void syncOutputsToBuffers()
{

//	TVP val = GET_FIELD(HardwareInterface,HardwareInterface,g_System_hwi,valveState);
	fmiBuffer.booleanBuffer[VALVE_ID]=GET_FIELD(HardwareInterface,HardwareInterface,g_System_hwi,valveState)->value.boolVal; // == 0 ? false : true;
}



void systemInit()
{
	HardwareInterface_const_init();
	Controller_const_init();
	System_static_init();

	sys = _Z6SystemEV(NULL);

}

void systemDeInit()
{
	HardwareInterface_const_shutdown();
	Controller_const_shutdown();
	System_static_shutdown();

	vdmFree(sys);
}
#endif

//#GENERATED_SYSTEM_INIT

//#GENERATED_SYSTEM_SHUTDOWN



fmi2Status vdmStep(fmi2Real currentCommunicationPoint, fmi2Real communicationStepSize)
{
	int numThreads = (sizeof threads) / (sizeof (struct PeriodicThreadStatus));
	int i, j;
	int threadRunCount;

	double currentCommunicationPoint = 0;
	double communicationStepSize = 1E8;

	//In this implementation we need the step size to be an integer multiple of the period of each thread.
	for(i = 0;  i < numThreads; i++)
	{
		if(((long int) communicationStepSize) % ((long int)threads[i].period) != 0)
		{
			return fmi2Discard;
		}
	}

	//Call each thread the appropriate number of times.
	for(i = 0;  i < numThreads; i++)
	{
		threadRunCount = ((long int) communicationStepSize) / ((long int)threads[i].period);

		//Execute each thread the number of times that its period fits in the step size.
		for(j = 0; j < threadRunCount; j++)
		{
//			CALL_FUNC(---OBJECT_TYPE---, ---OBJECT_TYPE---, threads[i].objectName, threads[i].callName);
		}

		//Update the thread's last execution time.
		threads[i].lastExecuted = currentCommunicationPoint + communicationStepSize;
	}

	return fmi2OK;
}

void systemMain()
{
	TVP world = _Z5WorldEV(NULL);
	CALL_FUNC(World, World, world, CLASS_World__Z3runEV);
	vdmFree(world);
}

void *PrintHello(void *threadid)
{
	while (true)
	{
		systemMain();
		usleep(10E3);
	}
}


#ifndef PERIODIC_GENERATED

struct PeriodicThreadStatus threads[] =
{
{ 1.0E7, "g_System_controller", "CLASS_System__Z4loopEV", 0 }
// 0+1.0E7 <=  currentCommunicationPoint+communicationStepSize+1.0E7 => execute and set executed Time
};


int main()
{
	systemInit();
	pthread_t thread = NULL;
	pthread_create(&thread, NULL, PrintHello, 0);
	while (1)
	{

		double a;
		scanf("%lf", &a);
		printf("Just read: %f\n", a);
		SET_FIELD(HardwareInterface, HardwareInterface, g_System_hwi, level, newReal(a));
	}
}

#endif
