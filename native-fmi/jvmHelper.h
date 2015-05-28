/*
 * jvmHelper.h
 *
 *  Created on: May 22, 2015
 *      Author: kel
 */

#ifndef NULL
#define NULL   ((void *) 0)
#endif

#ifndef JVMHELPER_H_
#define JVMHELPER_H_

#include <jni.h>



#ifdef _WIN32
	#include <windows.h>
#elif __APPLE__
    #include "TargetConditionals.h"
    #if TARGET_OS_MAC
        // Other kinds of Mac OS
		#include <dlfcn.h>
		#define HMODULE void*
    #else
        // Unsupported platform
    #endif
#elif __linux
	#include <limits.h>
	#include <dlfcn.h>
	#define MAX_PATH PATH_MAX
	#define HMODULE void*
#endif


JNIEnv* create_vm(JavaVM **jvm, const char* libPath,  char* jvmOption1);

int callJavaMethodInt(JNIEnv* env, const char* className, const char* methodName, const char* signature, ...);
void callJavaMethodVoid(JNIEnv* env, const char* className, const char* methodName, const char* signature, ...);
char callJavaMethodByte(JNIEnv* env, const char* className, const char* methodName, const char* signature, ...);

#endif /* JVMHELPER_H_ */
