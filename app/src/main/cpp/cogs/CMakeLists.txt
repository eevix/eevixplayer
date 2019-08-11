cmake_minimum_required(VERSION 3.4.1)

project(cogs)

include_directories(include)

aux_source_directory(src SOURCE_FILES)

add_library(${PROJECT_NAME}
            SHARED
            ${SOURCE_FILES})

add_compile_options(-Wall)

if (ANDROID)
    target_link_libraries(${PROJECT_NAME} log)
elseif (WIN32)
    target_link_libraries(${PROJECT_NAME} pthread)
endif ()

#add_subdirectory(tests)