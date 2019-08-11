# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add.library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

message("${CMAKE_SOURCE_DIR}")
message("${ANDROID_ABI}")

add_compile_options(-v -fno-rtti -fexceptions)

add_library(axtls
            STATIC
            IMPORTED)

set_target_properties(axtls
                      PROPERTIES IMPORTED_LOCATION
                      ./${ANDROID_ABI}/libaxTLS.a)

add_library(neptune
            STATIC
            IMPORTED)

set_target_properties(neptune
                      PROPERTIES IMPORTED_LOCATION
                      ./${ANDROID_ABI}/libNeptune.a)

add_library(platinum
            STATIC
            IMPORTED)

set_target_properties(platinum
                      PROPERTIES IMPORTED_LOCATION
                      ./${ANDROID_ABI}/libPlatinum.a)

add_library(zlib
            STATIC
            IMPORTED)

set_target_properties(zlib
                      PROPERTIES IMPORTED_LOCATION
                      ./${ANDROID_ABI}/libZlib.a)

add_library(pltmediarender
            STATIC
            IMPORTED)

set_target_properties(pltmediarender
                      PROPERTIES IMPORTED_LOCATION
                      ./${ANDROID_ABI}/libPltMediaRenderer.a)

add_library(pltmediaserver
            STATIC
            IMPORTED)

set_target_properties(pltmediaserver
                      PROPERTIES IMPORTED_LOCATION
                      ./${ANDROID_ABI}/libPltMediaServer.a)

add_library(test1 SHARED src/test/test.cpp)
target_link_libraries(test1 pltmediarender pltmediaserver platinum neptune axtls zlib)
