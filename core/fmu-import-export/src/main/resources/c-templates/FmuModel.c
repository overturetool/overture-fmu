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
fmi2Status vdmStep(fmi2Real currentCommunicationPoint_p, fmi2Real communicationStepSize_p)
{
	//convert seconds to nanoseconds
	long double currentCommunicationPoint = currentCommunicationPoint_p * 1E9;
	long double communicationStepSize = communicationStepSize_p * 1E9;

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
				threadRunCount = ((long long int)((long long int)currentCommunicationPoint + 
							(long long int)communicationStepSize - (long long int)threads[i].lastExecuted)) / ((long long int)threads[i].period);
				syncOutAllowed = fmi2True;
			}
			//Can not execute, but can sync existing values at the end of this step.
			else 
			{
				threadRunCount = 0;
				syncOutAllowed = fmi2True;
			}
		}
		//
		else
		{
			//Find number of executions to fit inside of step, allow sync.
			threadRunCount = ((long long int)((long long int)currentCommunicationPoint +
						(long long int)communicationStepSize -
							(long long int)threads[i].lastExecuted)) / ((long long int)threads[i].period);
			syncOutAllowed = fmi2True;

			//It is possible that one execution will overshoot the step.
			if(threadRunCount == 0)
			{
				threadRunCount = 1;
				syncOutAllowed = fmi2False;
			}
		}

		//Rounding calculation
		//if(((long long int)currentCommunicationPoint) - 2 <= ((long long int)(threads[i].lastExecuted)) && ((long long int)(threads[i].lastExecuted) <= ((long long int)currentCommunicationPoint) + 2))
			
		//Execute each thread the number of times that its period fits in the step size.
		for(j = 0; j < threadRunCount; j++)
		{
			threads[i].call();
			printf("NOW:  %Lf, TP: %Lf, LE:  %Lf, STEP:  %Lf, SYNC:  %d\n", currentCommunicationPoint / 1E9, threads[i].period / 1E9, threads[i].lastExecuted / 1E9, communicationStepSize / 1E9, syncOutAllowed);

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
