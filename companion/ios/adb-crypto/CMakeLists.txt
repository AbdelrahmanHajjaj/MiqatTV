cmake_minimum_required(VERSION 3.22)
if(POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)
endif()
project(MiqatAdbCrypto LANGUAGES C CXX)

include(FetchContent)
set(BUILD_TESTING OFF CACHE BOOL "Skip BoringSSL's upstream test suite" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static pairing libraries" FORCE)
set(BORINGSSL_PREFIX MiqatADB CACHE STRING "Isolate ADB crypto from OpenSSL used by LG SSH" FORCE)
if(IOS)
    # BoringSSL's unused bssl tool has no iOS bundle install destination.
    set(CMAKE_MACOSX_BUNDLE OFF)
endif()

FetchContent_Declare(boringssl
    URL https://codeload.github.com/google/boringssl/tar.gz/5ec099107553142e9cb34dfe52f3c5bb42c9b7fb
    URL_HASH SHA256=63f890385b2c9811ebba182d71358fa1c3bfc50280fda6a7b48776d418679681
)
FetchContent_MakeAvailable(boringssl)

add_library(miqat_adb_spake STATIC ../adb-spake.c ../adb-tls.c)
target_compile_definitions(miqat_adb_spake PRIVATE BORINGSSL_PREFIX=MiqatADB)
target_link_libraries(miqat_adb_spake PRIVATE ssl crypto)
install(TARGETS miqat_adb_spake ARCHIVE DESTINATION lib COMPONENT miqat-adb)
install(FILES "$<TARGET_FILE:ssl>" DESTINATION lib RENAME libmiqat_adb_tls.a COMPONENT miqat-adb)
install(FILES "$<TARGET_FILE:crypto>" DESTINATION lib RENAME libmiqat_adb_crypto.a COMPONENT miqat-adb)
install(FILES "${boringssl_SOURCE_DIR}/LICENSE" DESTINATION Notices RENAME BoringSSL.txt COMPONENT miqat-adb)

if(NOT CMAKE_CROSSCOMPILING)
    enable_testing()
    foreach(fixture spake tls)
        set(target miqat_adb_${fixture}_check)
        add_executable(${target} ../checks/adb-pairing/${fixture}.c)
        target_compile_definitions(${target} PRIVATE BORINGSSL_PREFIX=MiqatADB)
        target_link_libraries(${target} PRIVATE miqat_adb_spake ssl crypto)
        if(MSVC)
            target_compile_options(${target} PRIVATE /UNDEBUG)
        else()
            target_compile_options(${target} PRIVATE -UNDEBUG)
        endif()
        add_test(NAME adb_${fixture}_exchange COMMAND ${target})
    endforeach()
endif()
