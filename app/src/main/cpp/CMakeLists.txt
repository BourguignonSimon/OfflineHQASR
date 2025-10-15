cmake_minimum_required(VERSION 3.22.1)

project(whisper_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(whisper SHARED
    whisper_jni.cpp
)

find_library(log-lib log)
find_library(android-lib android)

if (log-lib)
    target_link_libraries(whisper PRIVATE ${log-lib})
endif()

if (android-lib)
    target_link_libraries(whisper PRIVATE ${android-lib})
endif()
