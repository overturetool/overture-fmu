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
    
    //convert to seconds
    stepSize = stepSize / 1E9;
    
    printf("Stepsize is: %f seconds.\n",stepSize);
    
    for (double time =0; time < totalTime; time=time+stepSize) {
        
            if(fmi2OK !=fmi2DoStep(NULL,time,stepSize,false))
            {
                printf("Step did not return ok\n");
                return 1;
            }
    }
    printf("Done\n");
  
}


#endif