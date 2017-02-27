#ifndef FMU_MODEL_H_
#define FMU_MODEL_H_

//#GENERATED_MODEL_INCLUDE

void syncInputsToModel();
void syncOutputsToBuffers();
void systemInit();
void systemDeInit();

extern struct PeriodicThreadStatus threads[];

#endif FMU_MODEL_H_
