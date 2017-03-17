/*
 * watertankFmu.cpp
 *
 *  Created on: Mar 3, 2016
 *      Author: kel
 */
#include "Fmu.h"
#include "FmuModel.h"

//#GENERATED_DEFINES


#include <stdarg.h>
#include "Fmu.h"
#include "Vdm.h"

//#GENERATED_MODEL_INCLUDE

TVP sys = NULL;
fmi2Boolean syncOutAllowed = fmi2True;

//#GENERATED_INSERT

//#GENERATED_SYSTEM_INIT

//#GENERATED_SYSTEM_SHUTDOWN


/*
* Both time value are given in seconds
*/
fmi2Status vdmStep(fmi2Real currentCommunicationPoint, fmi2Real communicationStepSize)
{
	//convert seconds to nanoseconds
	currentCommunicationPoint = currentCommunicationPoint*1E9;
	communicationStepSize = communicationStepSize*1E9;

	int i, j;
	int threadRunCount;


	//We want to be able to align synchronization on either step size or thread boundary.
	for(i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
	{
		if(
			(communicationStepSize >= threads[i].period) &&
			(((long long int) communicationStepSize) % ((long long int)threads[i].period) != 0))
		{
			g_fmiCallbackFunctions->logger((void*) 1,g_fmiInstanceName,fmi2Discard,"logError","%s\n", "Discarding step:  step size not integer multiple of thread period.");
			return fmi2Discard;
		}
		else if(
			(threads[i].period >= communicationStepSize) &&
			(((long long int)threads[i].period) % ((long long int) communicationStepSize) != 0))
		{
			g_fmiCallbackFunctions->logger((void*) 1,g_fmiInstanceName,fmi2Discard,"logError","%s\n", "Discarding step:  thread period not integer multiple of step size.");
			return fmi2Discard;
		}
	}


	//Call each thread the appropriate number of times.
	for(i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
	{
		if(communicationStepSize >= threads[i].period)
		{
			threadRunCount = ((long long int) communicationStepSize) / ((long long int)threads[i].period);
		}
		else
		{
			//Taking into account rounding errors.  Ideal condition is currentCommunicationPoint == threads[i].lastExecuted.
			if(((long long int)currentCommunicationPoint) - 2 <= ((long long int)(threads[i].lastExecuted)) && ((long long int)(threads[i].lastExecuted) <= ((long long int)currentCommunicationPoint) + 2))
			{
				threadRunCount = 1;
			}
			else
			{
				threadRunCount = 0;
			}
		}

		//Execute each thread the number of times that its period fits in the step size.
		for(j = 0; j < threadRunCount; j++)
		{
			threads[i].call();
//			printf("RUN THREAD AT %lf\n", currentCommunicationPoint / 1E9);

			//Update the thread's last execution time.
			threads[i].lastExecuted += threads[i].period;
		}

		vdm_gc();
	}

	return fmi2OK;
}

void systemMain()
{
	TVP world = _Z5WorldEV(NULL);
	CALL_FUNC(World, World, world, CLASS_World__Z3runEV);
	vdmFree(world);
}
