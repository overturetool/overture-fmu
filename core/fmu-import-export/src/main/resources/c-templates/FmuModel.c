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
fmi2Real maxStepSize = 0.0;

TVP newCharSeq(fmi2String str)
{
	TVP res;
	TVP *values;
	int i, len;

	len = strlen(str);
	values = calloc(len, sizeof(TVP));

	for(i = 0; i < len; i++)  values[i] = newChar(str[i]);

	res = newSeqWithValues(len, values);

	for(i = 0; i < len; i++)  vdmFree(values[i]);
	free(values);

	return res;
}

//#GENERATED_INSERT

//#GENERATED_SYSTEM_INIT

//#GENERATED_SYSTEM_SHUTDOWN


/*
* Both time value are given in seconds
*/
fmi2Status vdmStep(fmi2Real currentCommunicationPoint, fmi2Real communicationStepSize)
{


	int i, j;
	int threadRunCount[PERIODIC_GENERATED_COUNT];
	double dtmp;
	bool moreToExec;

	/*  Call each thread the appropriate number of times.  */
	for(i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
	{
		/*  Times align, sync took place last time.  */
		if(threads[i].lastExecuted >= currentCommunicationPoint)
		{
			/*  Can not do anything, still waiting for the last step's turn to come.  */
			if(threads[i].lastExecuted >= currentCommunicationPoint + communicationStepSize)
			{
				threadRunCount[i] = 0;
				syncOutAllowed = fmi2False;
			}
			/*  Previous step will finish inside this step.
			*   At least one execution can be fit inside this step.
			*/
			else if(threads[i].lastExecuted + threads[i].period <= currentCommunicationPoint + communicationStepSize)
			{
				/*  Find number of executions to fit inside of step, allow sync.  */
				dtmp = (currentCommunicationPoint + communicationStepSize - threads[i].lastExecuted) / threads[i].period;

				/*  Underflow  */
				if(dtmp - ((double)(int)dtmp) >= 0.99999)
					threadRunCount[i] = dtmp + 1;
				/*  Overflow  */
				else
					threadRunCount[i] = dtmp;

				syncOutAllowed = fmi2True;
			}
			/*  Can not execute, but can sync existing values at the end of this step.  */
			else 
			{
				threadRunCount[i] = 0;
				syncOutAllowed = fmi2True;
			}
		}
		else
		{
			/*  Find number of executions to fit inside of step, allow sync because need to update regardless.  */
			dtmp = (currentCommunicationPoint + communicationStepSize - threads[i].lastExecuted) / threads[i].period;
			syncOutAllowed = fmi2True;

			/*  Underflow  */
			if(dtmp - ((double)(int)dtmp) >= 0.99999)
				threadRunCount[i] = dtmp + 1;
			/*  Overflow  */
			else
				threadRunCount[i] = dtmp;

			/*  Period too long for this step so postpone until next step.  */
			if(threadRunCount[i] == 0)
				syncOutAllowed = fmi2False;
		}
	}

	do
	{
		moreToExec = false;
		for(j = 0; j < PERIODIC_GENERATED_COUNT; j++)
		{
			if(threadRunCount[j] != 0)
			{
				threads[j].call();
				threads[j].lastExecuted += threads[j].period;
				threadRunCount[j] -= 1;
	
				if(threadRunCount[j] != 0)
					moreToExec = true;
			}
		}
		vdm_gc();
	}while(moreToExec);


/*  g_fmiCallbackFunctions->logger(g_fmiCallbackFunctions->componentEnvironment, g_fmiInstanceName, fmi2OK, "logDebug", "\n\nTH:  %d, NOW:  %f, TP: %f, LE:  %f, STEP:  %f, SYNC:  %d, RUNS:  %d\n", i, currentCommunicationPoint, threads[i].period, threads[i].lastExecuted, communicationStepSize, syncOutAllowed, threadRunCount);  */

	maxStepSize = INT_MAX * 1.0;
	return fmi2OK;
}

void systemMain()
{
	TVP world = _Z5WorldEV(NULL);
	CALL_FUNC(World, World, world, CLASS_World__Z3runEV);
	vdmFree(world);
}
