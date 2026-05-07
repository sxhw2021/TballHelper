cmake_minimum_required(VERSION 3.18.1)

project("Billiards_SDK")

add_definitions(-DANDROID)
add_definitions(-DANDROID_STANDALONE)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")

if(NOT DEFINED OPENCV_ANDROID_SDK)
    set(OPENCV_ANDROID_SDK "${CMAKE_CURRENT_SOURCE_DIR}/../../../OpenCV-android-sdk")
endif()

set(OPENCV_INCLUDE_DIRS "${OPENCV_ANDROID_SDK}/sdk/native/jni/include")
set(OPENCV_LIBS_DIR "${OPENCV_ANDROID_SDK}/sdk/native/staticlib")

include_directories(${OPENCV_INCLUDE_DIRS})

find_library(log-lib log)
find_library(z-lib z)
find_library(opencv-lib opencv_world)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/jniLibs/${ANDROID_ABI})

add_library(Billiards_SDK SHARED
    native-lib.cpp
    template_matcher.cpp
    line_detector.cpp
    ball_detector.cpp
)

target_link_libraries(Billiards_SDK
    ${log-lib}
    ${z-lib}
    ${opencv-lib}
)

add_custom_command(TARGET Billiards_SDK POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
    ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libBilliards_SDK.so
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/
)
