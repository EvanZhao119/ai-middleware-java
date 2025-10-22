cmake_minimum_required(VERSION 3.10)
project(imageops)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

include_directories(${CMAKE_CURRENT_SOURCE_DIR})
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/third_party)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/build)

add_library(imageops SHARED imageops.cpp)
target_link_libraries(imageops ${JNI_LIBRARIES})

message(STATUS "JNI_INCLUDE_DIRS = ${JNI_INCLUDE_DIRS}")
message(STATUS "Output -> ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
