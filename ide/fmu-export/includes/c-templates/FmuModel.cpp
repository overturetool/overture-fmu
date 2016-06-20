/*
 * watertankFmu.cpp
 *
 *  Created on: Mar 3, 2016
 *      Author: kel
 */
#include "Fmu.h"

extern "C"
{
#include "Fmu.h"
#include "Vdm.h"
#include "LevelSensor.h"
#include "ValveActuator.h"
#include "Controller.h"
#include <unistd.h>

#include "System.h"
#include "World.h"
#include <pthread.h>
}

// FMI variable reference mapping
#define MIN_LEVEL_ID 1
#define MAX_LEVEL_ID 0

#define VALVE_ID 4
#define LEVEL_ID 3

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

#endif

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



void vdmStep(fmi2Real currentCommunicationPoint, fmi2Real communicationStepSize)
{

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

#endif

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

