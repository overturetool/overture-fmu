/*
 * jvmHelper.c
 *
 *  Created on: May 22, 2015
 *      Author: kel
 */
#include "jvmHelper.h"

#ifndef NULL
#define NULL   ((void *) 0)
#endif

char jvmLibLoaded = 0;
//typedef JNI_CreateJavaVM(JavaVM **pvm, void **penv, void *args);
//typedef _JNI_IMPORT_OR_EXPORT_ jint JNICALL (*myFuncDef)(JavaVM **pvm, void **penv, void *args);
typedef jint (JNICALL *CreateJavaVMPROC)(JavaVM **pvm, void **penv, void *args);

JNIEnv* create_vm(JavaVM **jvm, const char* jvmLibPath, char* jvmOption1)
{
	const char* m_JvmLibLocation =
			/*jvmLibPath;//*/"/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home/jre/lib/server/libjvm.dylib";

	printf("Loading lib from: %s\n", m_JvmLibLocation);
#ifdef _WIN32
	HINSTANCE m_hDllInstance = LoadLibraryA(m_JvmLibLocation);
#elif __APPLE__
#include "TargetConditionals.h"
#if TARGET_OS_MAC
	// Other kinds of Mac OS
	HMODULE m_hDllInstance = dlopen(m_JvmLibLocation,RTLD_LAZY);
#else
	// Unsupported platform
#endif
#elif __linux
	// linux
	HMODULE m_hDllInstance = dlopen(m_JvmLibLocation,RTLD_LAZY);
#endif

	const char* functionName = "JNI_CreateJavaVM";

#ifdef _WIN32
	void* fp = (void*)GetProcAddress(m_hDllInstance, functionName);
#elif __APPLE__
#include "TargetConditionals.h"
#if TARGET_OS_MAC
	// Other kinds of Mac OS
//		void* fp = (void*)dlsym(m_hDllInstance, functionName);
	CreateJavaVMPROC CreateJavaVM = (CreateJavaVMPROC) dlsym(m_hDllInstance, functionName);
#else
	// Unsupported platform
#endif
#elif __linux
	// linux
	void* fp = (void*)dlsym(m_hDllInstance, functionName);
#endif

	if (!CreateJavaVM)
	{
		printf("warning: Function %s not found in jvm library\n", functionName);
//        *success = 0;
	}

//    myFuncDef   *m_JVMInstance;
//
//    m_JVMInstance  =(myFuncDef )fp;

	JNIEnv* env;
	JavaVMInitArgs args;
	JavaVMOption options;
	args.version = JNI_VERSION_1_6;
	args.nOptions = 1;
//	options.optionString = "-Djava.class.path=./";
	options.optionString =
			/*jvmOption1;//*/"-Djava.class.path=/Users/kel/Downloads/eclipse-cde/workspace/JavaTest/bin/";

	args.options = &options;
	args.ignoreUnrecognized = 0;
	int rv;
//	rv = JNI_CreateJavaVM(jvm, (void**) &env, &args);
	rv = CreateJavaVM(jvm, (void**) &env, &args);
	if (rv < 0 || !env)
	{
		printf("Unable to Launch JVM %d\n", rv);

		//HACK, attach to current JVM hope it's there
		jsize nVMs;
		JNI_GetCreatedJavaVMs(NULL, 0, &nVMs); // 1. just get the required array length
		JavaVM** buffer = malloc(sizeof(JavaVM*) * nVMs);
		//new JavaVM*[nVMs];
		rv = JNI_GetCreatedJavaVMs(buffer, nVMs, &nVMs); // 2. get the data

		if (rv < 0)
		{
			printf(":-( could not attach to JNI_GetCreatedJavaVMs, dying...!\n");
		}

		printf("Got VM # %d\n", nVMs);
		jvm = buffer;
		printf("VM pointer %p\n", jvm);

	rv=	(*(*jvm))->GetEnv(*jvm, &env, JNI_VERSION_1_6);
	if (rv < 0)
			{
				printf("did not get env\n");
			}


	rv=	(*(*jvm))->AttachCurrentThread(*jvm,&env,NULL);
	if (rv < 0)
			{
				printf("did not attache\n");
			}

		printf("Lets try to add the class path...:%s\n",jvmOption1);
		callJavaMethodVoid(env,"org/intocps/orchestration/coe/util/ClassPathCLoader","addFileS","(Ljava/lang/String;)V",(*env)->NewStringUTF(env,jvmOption1));

	} else
		printf("Launched JVM! :)\n");
	return env;
}

int callJavaMethodInt(JNIEnv* env, const char* className, const char* methodName, const char* signature, ...)
{
	jclass jClz;
	jmethodID jMethod;
	jClz = (*env)->FindClass(env, className);

	jMethod = (*env)->GetStaticMethodID(env, jClz, methodName, signature);

	/* Declare a va_list type variable */
	va_list myargs;

	/* Initialise the va_list variable with the ... after fmt */

	va_start(myargs, signature);

//	(*env)->CallStaticVoidMethod(env, hello_world_class, main_method, NULL);
	int ret = (*env)->CallStaticIntMethodV(env, jClz, jMethod, myargs);

	/* Clean up the va_list */
	va_end(myargs);

	return ret;
}

void callJavaMethodVoid(JNIEnv* env, const char* className, const char* methodName, const char* signature, ...)
{
	jclass jClz;
	jmethodID jMethod;
	jClz = (*env)->FindClass(env, className);

	if (jClz == NULL)
		printf("Class not found\n");

	jMethod = (*env)->GetStaticMethodID(env, jClz, methodName, signature);

	if (jMethod == NULL)
		printf("Method not found\n");

	/* Declare a va_list type variable */
	va_list myargs;

	/* Initialise the va_list variable with the ... after fmt */

	va_start(myargs, signature);

//	(*env)->CallStaticVoidMethod(env, hello_world_class, main_method, NULL);
	(*env)->CallStaticVoidMethodV(env, jClz, jMethod, myargs);

	/* Clean up the va_list */
	va_end(myargs);

	return;
}

char callJavaMethodByte(JNIEnv* env, const char* className, const char* methodName, const char* signature, ...)
{
	jclass jClz;
	jmethodID jMethod;
	jClz = (*env)->FindClass(env, className);

	if (jClz == NULL)
		printf("Class not found\n");

	jMethod = (*env)->GetStaticMethodID(env, jClz, methodName, signature);

	if (jMethod == NULL)
		printf("Method not found\n");

	/* Declare a va_list type variable */
	va_list myargs;

	/* Initialise the va_list variable with the ... after fmt */

	va_start(myargs, signature);

//	(*env)->CallStaticVoidMethod(env, hello_world_class, main_method, NULL);
	char ret = (*env)->CallStaticByteMethodV(env, jClz, jMethod, myargs);

	/* Clean up the va_list */
	va_end(myargs);

	return ret;
}
