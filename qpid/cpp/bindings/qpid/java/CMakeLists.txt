#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

##--------------------------------------------------
## Properties used for generating the Java bindings.
##--------------------------------------------------
set(JAVA_PACKAGE_NAME org.apache.qpid.messaging.cpp.jni)
set(JAVA_PACKAGE_DIR org/apache/qpid/messaging/cpp/jni)
set(CMAKE_SWIG_FLAGS -package ${JAVA_PACKAGE_NAME})

##------------------------------------------------------
## Use Swig to generate a literal binding to the C++ API
##------------------------------------------------------

set_source_files_properties(${CMAKE_CURRENT_SOURCE_DIR}/java.i PROPERTIES CPLUSPLUS ON)
set_source_files_properties(${CMAKE_CURRENT_SOURCE_DIR}/java.i PROPERTIES SWIG_FLAGS "-I${qpid-cpp_SOURCE_DIR}/include")

swig_add_module(cqpid_java java ${CMAKE_CURRENT_SOURCE_DIR}/java.i)
swig_link_libraries(cqpid_java qpidmessaging qpidtypes ${JNI_LIBRARIES})

set_source_files_properties(${swig_generated_file_fullname} PROPERTIES COMPILE_FLAGS "-fno-strict-aliasing -I${JAVA_INCLUDE_PATH} -I${JAVA_INCLUDE_PATH2} -I${qpid-cpp_SOURCE_DIR}/include -I${CMAKE_CURRENT_SOURCE_DIR}")

##------------------------------------------------------
## Copy the jar file to java/lib dir
## TODO: need to add the qpid release version to the jar file
##------------------------------------------------------

add_custom_command(TARGET cqpid_java
                   POST_BUILD
                   COMMAND cmake -E echo "Compiling Java files..."
                   COMMAND cmake -E make_directory ${JAVA_PACKAGE_DIR}
                   COMMAND ${Java_JAVAC_EXECUTABLE} *.java -d ./
                   COMMAND cmake -E echo "Creating jar file..."
                   COMMAND ${Java_JAR_EXECUTABLE} cvf qpid_cpp_jni.jar org
                  )
