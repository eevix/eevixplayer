cmake_minimum_required(VERSION 3.4.1)

#gtest
add_library(gtest
			STATIC
			IMPORTED)

set_target_properties(gtest
					  PROPERTIES IMPORTED_LOCATION
					  ${CMAKE_CURRENT_SOURCE_DIR}/gtest/lib/libgtest.a)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../include)

include_directories(gtest/include)

aux_source_directory(${CMAKE_CURRENT_SOURCE_DIR} TEST_FILES)

message("SOURCE_FILES:${TEST_FILES}")

add_executable(test ${TEST_FILES})

target_link_libraries(test gtest cogs)