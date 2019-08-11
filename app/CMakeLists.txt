# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add.library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

# libcogs
set(COGS_PATH src/main/cpp/cogs)
add_subdirectory(${COGS_PATH})
include_directories(${COGS_PATH}/include/)

# libplatinum
set(PLATINUM_PATH ${CMAKE_SOURCE_DIR}/src/main/cpp/libs/libplatinum)
include_directories(${PLATINUM_PATH}/include/)

add_library(axtls
            SHARED
            IMPORTED)

set_target_properties(axtls
                      PROPERTIES IMPORTED_LOCATION
                      ${PLATINUM_PATH}/${ANDROID_ABI}/libaxTLS.a)

add_library(neptune
            SHARED
            IMPORTED)

set_target_properties(neptune
                      PROPERTIES IMPORTED_LOCATION
                      ${PLATINUM_PATH}/${ANDROID_ABI}/libNeptune.a)

add_library(platinum
            SHARED
            IMPORTED)

set_target_properties(platinum
                      PROPERTIES IMPORTED_LOCATION
                      ${PLATINUM_PATH}/${ANDROID_ABI}/libPlatinum.a)

add_library(zlib
            SHARED
            IMPORTED)

set_target_properties(zlib
                      PROPERTIES IMPORTED_LOCATION
                      ${PLATINUM_PATH}/${ANDROID_ABI}/libZlib.a)

add_library(pltmediarender
            SHARED
            IMPORTED)

set_target_properties(pltmediarender
                      PROPERTIES IMPORTED_LOCATION
                      ${PLATINUM_PATH}/${ANDROID_ABI}/libPltMediaRenderer.a)

add_library(pltmediaserver
            SHARED
            IMPORTED)

set_target_properties(pltmediaserver
                      PROPERTIES IMPORTED_LOCATION
                      ${PLATINUM_PATH}/${ANDROID_ABI}/libPltMediaServer.a)

#yoghurtjni
add_library(yoghurtjni
            SHARED
        src/main/cpp/jni/src/AndroidBundle.cpp)

include_directories(src/main/cpp/jni/include/)

target_link_libraries(yoghurtjni cogs)

#yoghurtmediarender
add_library(yoghurtmediarender
            SHARED
            src/main/cpp/YoghurtMediaRender.cpp)


target_link_libraries(yoghurtmediarender pltmediarender pltmediaserver platinum neptune axtls zlib yoghurtjni cogs log)