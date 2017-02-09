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

//#GENERATED_MODEL_INCLUDE
}

TVP sys = NULL;

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
			return fmi2Discard;
		}
		else if(
			(threads[i].period >= communicationStepSize) &&
			(((long long int)threads[i].period) % ((long long int) communicationStepSize) != 0))
		{
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
			//Taking into account rounding errors.
			if(((long long int)currentCommunicationPoint) - 2 <= ((long long int)(threads[i].lastExecuted)) && ((long long int)(threads[i].lastExecuted) <= ((long long int)currentCommunicationPoint) + 2))
			{
				threadRunCount = 1;
			}
			else
			{
				threadRunCount = 0;
			}
		}

//		printf("THREAD COUNT:  %d\nSTEP SIZE:  %lf\nCURRENT POINT:  %lf\nTHREAD PERIOD:  %llf\nLAST EXECUTED %llf\n", threadRunCount, communicationStepSize, currentCommunicationPoint, threads[i].period, threads[i].lastExecuted);

		//Execute each thread the number of times that its period fits in the step size.
		for(j = 0; j < threadRunCount; j++)
		{
			threads[i].call();
//			printf("RUN THREAD AT %lf\n", currentCommunicationPoint / 1E9);

			//Update the thread's last execution time.
			threads[i].lastExecuted += threads[i].period;
		}
	}

	return fmi2OK;
}

void systemMain()
{
	TVP world = _Z5WorldEV(NULL);
	CALL_FUNC(World, World, world, CLASS_World__Z3runEV);
	vdmFree(world);
}


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
    
    fmi2Instantiate("this system",fmi2CoSimulation,"","",&callback,fmi2True,fmi2True);
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
    
    //convert to seconds
    stepSize = stepSize / 1E9;
    
    printf("Stepsize is: %f seconds.\n",stepSize);
    
    for (double time =0; time < totalTime; time=time+stepSize) {
        
            if(fmi2OK !=fmi2DoStep(NULL,time,stepSize,fmi2False))
            {
                printf("Step did not return ok\n");
                return 1;
            }
    }
    printf("Done\n");
  
}


#endif
