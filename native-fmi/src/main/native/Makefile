
# Declaration of variables
JAVA_HOME?=/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home
CC? = gcc

JAVA_INCLUDE?=include/darwin
CC_FLAGS = -O0 -g -c -DFMI_COSIMULATION -I$(JAVA_HOME)/include -I$(JAVA_HOME)/$(JAVA_INCLUDE) -O0 -g3 -Wall -c -fmessage-length=0 -std=c99 -fPIC

#LIBS  = -ljvm
#CFLAGS = -Wall -shared -L/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home/jre/lib/server -DFMI_COSIMULATION -I/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home/include -I/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home/include/darwin -O0 -g3 -Wall -c -fmessage-length=0

LD_LIBS = -ljvm
JAVA_JVM_LIB_PATH?=server
LDFLAGS = -shared -L$(JAVA_HOME)/jre/$(JAVA_JVM_LIB_PATH)
LINK.so = $(CC) $(LDFLAGS) $(LD_LIBS)

LIB_FOLDER?=darwin64
C_BUILD_DIR?=../../../target/native-$(LIB_FOLDER)


LIB_PREFIX?= 
LIB_EXTN?=.dylib

FMU_BIN_DIR?=../../../target/fmu/binaries/$(LIB_FOLDER)
LIBNAME?=fmi2java

# File names
EXEC = $(FMU_BIN_DIR)/$(LIB_PREFIX)$(LIBNAME)$(LIB_EXTN)
SOURCES = $(wildcard *.c)
#OBJECTS = $(SOURCES:.c=.o)

OBJECTS = $(addprefix $(C_BUILD_DIR)/,$(subst .c,.o,$(SOURCES)))

all : $(EXEC)

# Main target
$(EXEC): $(OBJECTS)
#	$(CC) $(OBJECTS) -o $(EXEC)
	@mkdir -p `dirname $@`
	$(LINK.so) $(OUTPUT_OPTION) $^

# To obtain object files
$(C_BUILD_DIR)/%.o: %.c
	@mkdir -p `dirname $@`
	echo $<
	$(CC) -c $(CC_FLAGS) $< -o $@

# To remove generated files
clean:
	rm -f $(EXEC) $(OBJECTS)
