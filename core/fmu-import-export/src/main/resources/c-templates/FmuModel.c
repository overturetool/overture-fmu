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
	int threadRunCount;
	double dtmp;

	/*  Call each thread the appropriate number of times.  */
	for(i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
	{
		/*  Times align, sync took place last time.  */
		if(threads[i].lastExecuted >= currentCommunicationPoint)
		{
			/*  Can not do anything, still waiting for the last step's turn to come.  */
			if(threads[i].lastExecuted >= currentCommunicationPoint + communicationStepSize)
			{
				threadRunCount = 0;
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
					threadRunCount = dtmp + 1;
				/*  Overflow  */
				else
					threadRunCount = dtmp;

				syncOutAllowed = fmi2True;
			}
			/*  Can not execute, but can sync existing values at the end of this step.  */
			else 
			{
				threadRunCount = 0;
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
				threadRunCount = dtmp + 1;
			/*  Overflow  */
			else
				threadRunCount = dtmp;

			/*  Period too long for this step so postpone until next step.  */
			if(threadRunCount == 0)
			{
				syncOutAllowed = fmi2False;
			}
		}		

		/*  Execute each thread the number of times that its period fits in the step size.  */
		for(j = 0; j < threadRunCount; j++)
		{
			threads[i].call();

			/*  Update the thread's last execution time.  */
			threads[i].lastExecuted += threads[i].period;
		}

		vdm_gc();
	}

	/* Calculate maximum step size for next step.  Cyclic controllers with no feedback do not have
	a limit on how large a step they can take.  To be considered in the future for controllers
	with feedback.
	*/
	maxStepSize = INT_MAX * 1.0;

	/*  g_fmiCallbackFunctions->logger(g_fmiCallbackFunctions->componentEnvironment, g_fmiInstanceName, fmi2OK, "logDebug", "NOW:  %f, TP: %f, LE:  %f, STEP:  %f, SYNC:  %d, RUNS:  %d\n", currentCommunicationPoint, threads[0].period, threads[0].lastExecuted, communicationStepSize, syncOutAllowed, threadRunCount);  */

	return fmi2OK;
}

void systemMain()
{
	TVP world = _Z5WorldEV(NULL);
	CALL_FUNC(World, World, world, CLASS_World__Z3runEV);
	vdmFree(world);
}
