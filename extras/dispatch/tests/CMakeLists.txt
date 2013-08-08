##
## Licensed to the Apache Software Foundation (ASF) under one
## or more contributor license agreements.  See the NOTICE file
## distributed with this work for additional information
## regarding copyright ownership.  The ASF licenses this file
## to you under the Apache License, Version 2.0 (the
## "License"); you may not use this file except in compliance
## with the License.  You may obtain a copy of the License at
## 
##   http://www.apache.org/licenses/LICENSE-2.0
## 
## Unless required by applicable law or agreed to in writing,
## software distributed under the License is distributed on an
## "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
## KIND, either express or implied.  See the License for the
## specific language governing permissions and limitations
## under the License.
##

##
## Build test applications
##
set(unit_test_SOURCES
    alloc_test.c
    parse_test.c
    run_unit_tests.c
    server_test.c
    timer_test.c
    tool_test.c
    )

add_executable(unit_tests ${unit_test_SOURCES})
target_link_libraries(unit_tests qpid-dispatch)

set(unit_test_size_SOURCES
    field_test.c
    message_test.c
    run_unit_tests_size.c
    )

add_executable(unit_tests_size ${unit_test_size_SOURCES})
target_link_libraries(unit_tests_size qpid-dispatch)

add_test(unit_tests_size_10000 unit_tests_size 10000)
add_test(unit_tests_size_512   unit_tests_size 512)
add_test(unit_tests_size_10    unit_tests_size 10)
add_test(unit_tests_size_7     unit_tests_size 7)
add_test(unit_tests_size_5     unit_tests_size 5)
add_test(unit_tests_size_3     unit_tests_size 3)
add_test(unit_tests_size_2     unit_tests_size 2)
add_test(unit_tests_size_1     unit_tests_size 1)
add_test(unit_tests            unit_tests ${CMAKE_CURRENT_SOURCE_DIR}/threads4.conf)
add_test(router_tests          python ${CMAKE_CURRENT_SOURCE_DIR}/router_engine_test.py -v)
add_test(system_tests_single   python ${CMAKE_CURRENT_SOURCE_DIR}/system_tests_one_router.py -v)

set_property(TEST system_tests_single PROPERTY
    ENVIRONMENT "CTEST_SOURCE_DIR=${CMAKE_CURRENT_SOURCE_DIR}"
    )
