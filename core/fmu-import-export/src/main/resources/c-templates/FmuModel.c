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
	int i, j;
	int threadRunCount;


	//Call each thread the appropriate number of times.
	for(i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
	{
		//Times align, sync took place last time.
		if(threads[i].lastExecuted >= currentCommunicationPoint)
		{
			//Can not do anything, still waiting for the last step's turn to come.
			if(threads[i].lastExecuted >= currentCommunicationPoint + communicationStepSize)
			{
				threadRunCount = 0;
				syncOutAllowed = fmi2False;
			}
			//Previous step will finish inside this step.
			//At least one execution can be fit inside this step.
			else if(threads[i].lastExecuted + threads[i].period <= currentCommunicationPoint + communicationStepSize)
			{
				//Find number of executions to fit inside of step, allow sync.
				threadRunCount = (currentCommunicationPoint + communicationStepSize - threads[i].lastExecuted) / threads[i].period;
				syncOutAllowed = fmi2True;
			}
			//Can not execute, but can sync existing values at the end of this step.
			else 
			{
				threadRunCount = 0;
				syncOutAllowed = fmi2True;
			}
		}
		else
		{
			//Find number of executions to fit inside of step, allow sync because need to update regardless.
			threadRunCount = (currentCommunicationPoint + communicationStepSize - threads[i].lastExecuted) / threads[i].period;
			syncOutAllowed = fmi2True;

			//Period too long for this step so postpone until next step.
			if(threadRunCount == 0)
			{
				syncOutAllowed = fmi2False;
			}
		}

			
		//printf("NOW:  %Lf, TP: %Lf, LE:  %Lf, STEP:  %Lf, SYNC:  %d, RUNS:  %d\n", currentCommunicationPoint / 1E9, threads[i].period / 1E9, threads[i].lastExecuted / 1E9, communicationStepSize / 1E9, syncOutAllowed, threadRunCount);
		//printf("NOW:  %f, TP: %f, LE:  %f, STEP:  %f, SYNC:  %d, RUNS:  %d\n", currentCommunicationPoint, threads[i].period, threads[i].lastExecuted, communicationStepSize, syncOutAllowed, threadRunCount);

		//Execute each thread the number of times that its period fits in the step size.
		for(j = 0; j < threadRunCount; j++)
		{
			threads[i].call();

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
