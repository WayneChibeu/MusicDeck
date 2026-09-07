cmake_minimum_required(VERSION 3.22.1)

project("musicdeck_audio")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Maximum performance optimization for real-time DSP
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -Wall")

add_library(
    musicdeck_audio
    SHARED
    musicdeck_jni.cpp
    dsp/EqualizerEngine.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    musicdeck_audio
    ${log-lib}
)
