cmake_minimum_required (VERSION 2.8.5)
project (##PROJECT_NAME## C CXX)


add_definitions(-DCUSTOM_IO -DPERIODIC_GENERATED -DFMI_COSIMULATION)


set(CMAKE_INCLUDE_CURRENT_DIR on)


IF(CMAKE_COMPILER_IS_GNUCC)
  SET(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fmessage-length=0")
ENDIF(CMAKE_COMPILER_IS_GNUCC)
IF(CMAKE_COMPILER_IS_GNUCXX)
  SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fmessage-length=0")
ENDIF(CMAKE_COMPILER_IS_GNUCXX)

SET(CMAKE_VERBOSE_MAKEFILE ON)

file(GLOB C_FILES vdmlib/*.c *.c) 
file(GLOB CPP_FILES *.cpp)

add_library(${PROJECT_NAME} SHARED ${C_FILES} ${CPP_FILES})

#if (UNIX)
#        if(APPLE)
#
#set(LIBRARY_OUTPUT_PATH ${CMAKE_CURRENT_SOURCE_DIR}/../binaries/darwin64)
#   ELSE()
#set(LIBRARY_OUTPUT_PATH ${CMAKE_CURRENT_SOURCE_DIR}/../binaries/linux64)
#ENDIF()
#ENDIF()

include_directories(fmi)
include_directories(vdmlib)


foreach(LETTER ${CMAKE_CXX_IMPLICIT_LINK_DIRECTORIES})
	
if(EXISTS "${LETTER}/libstdc++.a")
#   message (STATUS "${LETTER}")
	set(STDCPP_LIBRARY "${LETTER}/libstdc++.a")
else()
#  message (STATUS "${LETTER} ---- not these")
endif()
endforeach()

message(STATUS "Static linking with libstdc++.a in ${STDCPP_LIBRARY}")
target_link_libraries(${PROJECT_NAME} ${STDCPP_LIBRARY})


#IF(NOT(DEFINED XOSX))

if (UNIX)
        if(APPLE)


     SET(PTHREAD_LIBRARY libpthread.dylib)
	 ELSE()
		 find_library(PTHREAD_LIBRARY NAMES libpthread.a libpthread.dylib)
		 

ENDIF()
ENDIF()

target_link_libraries(${PROJECT_NAME} ${PTHREAD_LIBRARY})

# I need this: -static-libgcc -static-libstdc++ -Wl,-Bstatic -lstdc++ -lpthread -Wl,-Bdynamic

if (WIN32)
	#windows	
	set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libstdc++ -static-libgcc")
endif() 


if (UNIX)
	if(APPLE)

else()


set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC -lm")
set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC -lm")

endif()
endif()

set_target_properties(${PROJECT_NAME}
                      PROPERTIES POSITION_INDEPENDENT_CODE ON, PREFIX "")


add_executable(${PROJECT_NAME}-standalone ${C_FILES} ${CPP_FILES})
target_link_libraries(${PROJECT_NAME}-standalone ${PTHREAD_LIBRARY})
target_link_libraries(${PROJECT_NAME}-standalone ${STDCPP_LIBRARY})

if (UNIX)
	if(APPLE)
	else()
		# link against libm to provide the math library function implementation
		target_link_libraries(${PROJECT_NAME}-standalone m)
	endif()
endif()
