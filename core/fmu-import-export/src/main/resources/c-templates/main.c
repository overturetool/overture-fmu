#include <stdarg.h>
#include "Fmu.h"
#include "Vdm.h"
#include "FmuModel.h"



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
		systemDeInit();
                return 1;
            }

	vdm_gc();
    }

    systemDeInit();

    printf("Done\n"); 
}
