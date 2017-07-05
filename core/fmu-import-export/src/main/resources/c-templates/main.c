#include <stdarg.h>
#include "Fmu.h"
#include "Vdm.h"
#include "FmuModel.h"
#include "float.h"



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
    int i;
    double time;

    fmi2CallbackFunctions callback={&fmuLoggerCache,NULL,NULL,NULL,NULL};
    
    fmi2Instantiate("this system",fmi2CoSimulation, _FMU_GUID,"",&callback,fmi2True,fmi2True);
    systemInit();
    syncInputsToModel();
    
    double stepSize = 0;
    double totalTime = 10E9;
    
    
    for(i = 0;  i < PERIODIC_GENERATED_COUNT; i++)
    {
        if(stepSize < threads[i].period)
        {
            stepSize = threads[i].period;
        }
    }
    
    /*  convert to seconds  */
    stepSize = stepSize / 1E9;
    
    printf("Stepsize is: %f seconds.\n",stepSize);
    
    for (time =0; time < totalTime; time=time+stepSize) {
        
            if(fmi2OK !=fmi2DoStep(NULL,time,stepSize,fmi2False))
            {
                printf("Step did not return ok at time: %f\n", time);
				systemDeInit();
                return 1;
            }

		vdm_gc();
	
		//adjust for overflow it will overflow at 10E29 years if time is stepped in seconds as required
		if(time + stepSize > DBL_MAX -stepSize)
		{
			time = 0;
		}
    }

    systemDeInit();

    printf("Done\n"); 
}
