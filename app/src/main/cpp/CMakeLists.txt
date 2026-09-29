cmake_minimum_required(VERSION 3.22.1)

project("scale_protocol" C)

add_library(scale_protocol SHARED
    scale_protocol.c
    scale_protocol_jni.c
)

target_include_directories(scale_protocol PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)

target_link_libraries(scale_protocol ${log-lib})
