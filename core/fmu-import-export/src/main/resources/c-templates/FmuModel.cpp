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
#include <stdarg.h>
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
	int i, j;
	int threadRunCount;

	//In this implementation we need the step size to be an integer multiple of the period of each thread.
	for(i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
	{
		if(((long int) communicationStepSize) % ((long int)threads[i].period) != 0)
		{
			return fmi2Discard;
		}
	}

	//Call each thread the appropriate number of times.
	for(i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
	{
		threadRunCount = ((long int) communicationStepSize) / ((long int)threads[i].period);

		//Execute each thread the number of times that its period fits in the step size.
		for(j = 0; j < threadRunCount; j++)
		{
			threads[i].call();
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
{ 1.0E7, the_call_name, 0 }
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

#ifdef PERIODIC_GENERATED


void fmuLoggerCache(void *componentEnvironment, fmi2String instanceName, fmi2Status status, fmi2String category,
                    fmi2String message, ...)
{
    va_list(args);
    
    va_start(args, message);
    vprintf(message, args);
    va_end(args);
}


int main()
{
    fmi2CallbackFunctions callback={&fmuLoggerCache,NULL,NULL,NULL,NULL};
    
    fmi2Instantiate("this system",fmi2CoSimulation,"","",&callback,true,true);
    systemInit();
    syncInputsToModel();
    
    double stepSize = 0;
    double totalTime = 10E9;
    
    
    for(int i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
    {
        if(stepSize < threads[i].period)
        {
            stepSize = threads[i].period;
        }
    }
    
    printf("Stepsize is: %f\n",stepSize);
    
    for (double time =0; time < totalTime; time=time+stepSize) {
        
            if(fmi2OK !=vdmStep(time,stepSize))
            {
                printf("Step did not return ok\n");
                return 1;
            }
    }
    printf("Done\n");
  
}


#endif