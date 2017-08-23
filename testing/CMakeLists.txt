cmake_minimum_required (VERSION 2.8.5)
project (##NAME## C CXX)

set(CMAKE_INCLUDE_CURRENT_DIR on)

##DEFINITIONS##

include(CheckCXXCompilerFlag)
CHECK_CXX_COMPILER_FLAG("-std=c++11" COMPILER_SUPPORTS_CXX11)
CHECK_CXX_COMPILER_FLAG("-std=c++0x" COMPILER_SUPPORTS_CXX0X)
if(COMPILER_SUPPORTS_CXX11)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
elseif(COMPILER_SUPPORTS_CXX0X)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++0x")
else()
        message(STATUS "The compiler ${CMAKE_CXX_COMPILER} has no C++11 support. Please use a different C++ compiler.")
endif()


IF(CMAKE_COMPILER_IS_GNUCC)
  SET(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fmessage-length=0 -std=c11")
ENDIF(CMAKE_COMPILER_IS_GNUCC)
IF(CMAKE_COMPILER_IS_GNUCXX)
  SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fmessage-length=0")
ENDIF(CMAKE_COMPILER_IS_GNUCXX)

SET(CMAKE_VERBOSE_MAKEFILE ON)

file(GLOB C_FILES sources/*.c sources/**/*.c) # ../includes/templates/*.c)
file(GLOB CPP_FILES sources/*.cpp sources/**/*.cpp)

add_library(${PROJECT_NAME} SHARED ${C_FILES} ${CPP_FILES})

include_directories(sources $ENV{FMI_INCLUDES}/fmi $ENV{FMI_INCLUDES}/templates)

##INCLUDES##

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

#target_link_libraries(${PROJECT_NAME} ${PTHREAD_LIBRARY})

# I need this: -static-libgcc -static-libstdc++ -Wl,-Bstatic -lstdc++ -lpthread -Wl,-Bdynamic

if (WIN32)
	#windows	
	set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libstdc++ -static-libgcc")
endif() 


if (UNIX)
	if(APPLE)

else()


set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")
set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
target_link_libraries(${PROJECT_NAME} m)

endif()
endif()

set_target_properties(${PROJECT_NAME}
                      PROPERTIES POSITION_INDEPENDENT_CODE ON, PREFIX "")
