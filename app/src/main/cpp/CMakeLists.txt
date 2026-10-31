cmake_minimum_required(VERSION 3.22.1)
project("nucleusflow")

add_library(
        nucleusflow
        SHARED
        nucleus_jni.cpp
)

find_library(
        log-lib
        log
)

if(log-lib)
    target_link_libraries(
            nucleusflow
            ${log-lib}
    )
endif()
