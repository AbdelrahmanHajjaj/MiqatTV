cmake_minimum_required(VERSION 3.22)
if(POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)
endif()
project(MiqatAdbCrypto LANGUAGES C CXX)

include(FetchContent)
set(BUILD_TESTING OFF CACHE BOOL "Skip BoringSSL's upstream test suite" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static pairing libraries" FORCE)
set(BORINGSSL_PREFIX MiqatADB CACHE STRING "Isolate ADB crypto from OpenSSL used by LG SSH" FORCE)

FetchContent_Declare(boringssl
    URL https://codeload.github.com/google/boringssl/tar.gz/5ec099107553142e9cb34dfe52f3c5bb42c9b7fb
    URL_HASH SHA256=63f890385b2c9811ebba182d71358fa1c3bfc50280fda6a7b48776d418679681
)
FetchContent_MakeAvailable(boringssl)

add_library(miqat_adb_spake STATIC ../adb-spake.c)
target_compile_definitions(miqat_adb_spake PRIVATE BORINGSSL_PREFIX=MiqatADB)
target_link_libraries(miqat_adb_spake PRIVATE crypto)

if(NOT CMAKE_CROSSCOMPILING)
    add_executable(miqat_adb_spake_check ../checks/adb-pairing/spake.c)
    target_link_libraries(miqat_adb_spake_check PRIVATE miqat_adb_spake)
    if(MSVC)
        target_compile_options(miqat_adb_spake_check PRIVATE /UNDEBUG)
    else()
        target_compile_options(miqat_adb_spake_check PRIVATE -UNDEBUG)
    endif()
    enable_testing()
    add_test(NAME adb_spake_exchange COMMAND miqat_adb_spake_check)
endif()
