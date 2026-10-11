cmake_minimum_required(VERSION 3.18)
project(dolrecomp_android C)

set(DOLRECOMP_DIR "" CACHE PATH "DolRecomp checkout (with submodules)")
if(NOT EXISTS "${DOLRECOMP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "DolRecomp not found at '${DOLRECOMP_DIR}'. CI checks it out into native/DolRecomp.")
endif()

# C backend only; the LLVM backend needs LLVM 19/20 built for Android.
set(DOLRECOMP_ENABLE_LLVM OFF CACHE BOOL "" FORCE)

# EXCLUDE_FROM_ALL: build only the libraries we link, not DolRecomp's tests/tools.
add_subdirectory(${DOLRECOMP_DIR} dolrecomp EXCLUDE_FROM_ALL)

# Reuse upstream main() unmodified by renaming it at compile time.
set_source_files_properties(${DOLRECOMP_DIR}/src/app/main.c
    PROPERTIES COMPILE_DEFINITIONS "main=dolrecomp_main")

add_library(dolrecomp_jni SHARED
    jni_bridge.c
    ${DOLRECOMP_DIR}/src/app/main.c)
target_link_libraries(dolrecomp_jni PRIVATE dr_app log)
# 16 KB page-size compatible (Android 15+)
target_link_options(dolrecomp_jni PRIVATE -Wl,-z,max-page-size=16384)
