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
    message_test.c
    run_tests.c
    server_test.c
    timer_test.c
    tool_test.c
    )

add_executable(unit_tests ${unit_test_SOURCES})
target_link_libraries(unit_tests qpid-dispatch)

add_test(unit_tests_buf_512 unit_tests 512)
add_test(unit_tests_buf_10K unit_tests 10000)
add_test(unit_tests_buf_10  unit_tests 10)
add_test(unit_tests_buf_1   unit_tests 1)
