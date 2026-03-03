cmake_minimum_required(VERSION 3.22.1)
project(edge_ai_voice)

add_library(native-lib SHARED native-lib.cpp)

target_include_directories(native-lib PRIVATE
    "${CMAKE_SOURCE_DIR}/third_party/include"
)

# Ensure app-built JNI library is compatible with 16 KB page-size devices.
if (ANDROID)
    target_link_options(native-lib PRIVATE
        -Wl,-z,common-page-size=16384
        -Wl,-z,max-page-size=16384
    )
endif()

find_library(log-lib log)
find_library(dl-lib dl)

set(JNI_LIBS_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

add_library(llama-lib SHARED IMPORTED)
set_target_properties(llama-lib PROPERTIES
    IMPORTED_LOCATION "${JNI_LIBS_DIR}/libllama.so"
)

add_library(whisper-lib SHARED IMPORTED)
set_target_properties(whisper-lib PROPERTIES
    IMPORTED_LOCATION "${JNI_LIBS_DIR}/libwhisper.so"
)

target_link_libraries(native-lib
    ${log-lib}
    ${dl-lib}
    llama-lib
    whisper-lib
)
