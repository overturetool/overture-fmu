/*
 * main.c
 *
 *  Created on: May 22, 2015
 *      Author: kel
 */
#include <stdio.h>
#include "fmi2Functions.h"
//#include <jni.h>
//
//#ifndef NULL
//#define NULL   ((void *) 0)
//#endif

#include "jvmHelper.h"

//int main()
//{
//	// code
//
//	printf("hellow world");
//	return 0; // Zero indicates success, while any
//	// Non-Zero value indicates a failure/error
//}

//JNIEnv* create_vm(JavaVM **jvm)
//{
//	JNIEnv* env;
//	JavaVMInitArgs args;
//	JavaVMOption options;
//	args.version = JNI_VERSION_1_6;
//	args.nOptions = 1;
//	options.optionString = "-Djava.class.path=./";
//	args.options = &options;
//	args.ignoreUnrecognized = 0;
//	int rv;
//	rv = JNI_CreateJavaVM(jvm, (void**) &env, &args);
//	if (rv < 0 || !env)
//		printf("Unable to Launch JVM %d\n", rv);
//	else
//		printf("Launched JVM! :)\n");
//	return env;
//}
//
void invoke_class(JNIEnv* env)
{
	jclass hello_world_class;
	jmethodID main_method;
	jmethodID square_method;
	jmethodID power_method;
	jint number = 20;
	jint exponent = 3;
	hello_world_class = (*env)->FindClass(env, "HelloWorld");
	main_method = (*env)->GetStaticMethodID(env, hello_world_class, "main", "([Ljava/lang/String;)V");
	square_method = (*env)->GetStaticMethodID(env, hello_world_class, "square", "(I)I");
	power_method = (*env)->GetStaticMethodID(env, hello_world_class, "power", "(II)I");
	(*env)->CallStaticVoidMethod(env, hello_world_class, main_method, NULL);
	printf("%d squared is %d\n", number, (*env)->CallStaticIntMethod(env, hello_world_class, square_method, number));
	printf("%d raised to the %d power is %d\n", number, exponent,
			(*env)->CallStaticIntMethod(env, hello_world_class, power_method, number, exponent));
}
//
//int callJavaMethod(JNIEnv* env, const char* className, const char* methodName, const char* signature, ...)
//{
//	jclass jClz;
//	jmethodID jMethod;
//	jClz = (*env)->FindClass(env, className);
//
//	jMethod = (*env)->GetStaticMethodID(env, jClz, methodName, signature);
//
//	/* Declare a va_list type variable */
//	va_list myargs;
//
//	/* Initialise the va_list variable with the ... after fmt */
//
//	va_start(myargs, signature);
//
////	(*env)->CallStaticVoidMethod(env, hello_world_class, main_method, NULL);
//	int ret = (*env)->CallStaticIntMethodV(env, jClz, jMethod, myargs);
//
//	/* Clean up the va_list */
//	va_end(myargs);
//
//	return ret;
//}

int main()
{
//	JavaVM *jvm;
//	JNIEnv *env;
//	env = create_vm(&jvm);
//	if (env == NULL)
//		return 1;
//	invoke_class(env);
//
//	printf("final test");
//int status=	callJavaMethod(env,"HelloWorld","square","(I)I",20);
//printf("%d ",status);

	fmi2Instantiate("instnce 2", fmi2CoSimulation, "{1234}", ".", NULL, 0, 1);

	fmi2EnterInitializationMode(0);

	int vr[] =
	{ 1, 2 };
	double vals[] =
	{ 1.1, 2.2 };

	fmi2SetReal(NULL, vr, 2, vals);

	printf("calling get real\n");
	fmi2Real *r = (double *) malloc(sizeof(double) * 2);
	fmi2GetReal(NULL, vr, 2, r);

	int i =0;
	for(i=0;i<2;i++)
		printf("%4.2f\n",r[i]);

	printf("done\n");
	return 0;
}
