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

PROJECT (python-qpid_message)

CMAKE_MINIMUM_REQUIRED (VERSION 2.4.0 FATAL_ERROR)

INCLUDE (FindSWIG REQUIRED)
INCLUDE (UseSWIG)
INCLUDE (FindPythonInterp REQUIRED)
INCLUDE (FindPythonLibs REQUIRED)

## -------------
## Set variables
## -------------

EXECUTE_PROCESS(COMMAND ${PYTHON_EXECUTABLE}
  -c "from distutils.sysconfig import get_python_lib; print get_python_lib(True, prefix='${CMAKE_INSTALL_PREFIX}')"
  OUTPUT_VARIABLE PYTHON_SITEARCH_PACKAGES
  OUTPUT_STRIP_TRAILING_WHITESPACE)

FIND_PATH (QPID_INCLUDE_DIR qpid/qpid.i)
FIND_LIBRARY (QPID_LIBRARY_DIR
  qpidmessaging
  qpidtypes)

## --------------------------
## Generate the Swig bindings
## --------------------------

SET_SOURCE_FILES_PROPERTIES (${CMAKE_CURRENT_SOURCE_DIR}/python.i
  PROPERTIES CPLUSPLUS ON)
SET_SOURCE_FILES_PROPERTIES (${CMAKE_CURRENT_SOURCE_DIR}/python.i
  PROPERTIES SWIG_FLAGS "-I${QPID_INCLUDE_DIR}")

SWIG_ADD_MODULE (qpid_messaging_python python
  ${CMAKE_CURRENT_SOURCE_DIR}/python.i)

SWIG_LINK_LIBRARIES (qpid_messaging_python
  qpidmessaging
  qpidtypes
  ${PYTHON_LIBRARIES})

## --------------------
## Install the bindings
## --------------------

INSTALL (CODE "EXECUTE_PROCESS(COMMAND ${PYTHON_EXECUTABLE} -m py_compile qpid_messaging.py
                               WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR})")
INSTALL (CODE "EXECUTE_PROCESS(COMMAND ${PYTHON_EXECUTABLE} -O -m py_compile qpid_messaging.py
                               WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR})")

SET_SOURCE_FILES_PROPERTIES (${swig_generated_file_fullname}
  PROPERTIES COMPILE_FLAGS "-fno-strict-aliasing")

INCLUDE_DIRECTORIES (
  ${PYTHON_INCLUDE_PATH}
  $QPID_INCLUDE_PATH}
  ${CMAKE_CURRENT_SOURCE_DIR}
)

INSTALL (FILES ${CMAKE_CURRENT_BINARY_DIR}/qpid_messaging.py
               ${CMAKE_CURRENT_BINARY_DIR}/qpid_messaging.pyc
               ${CMAKE_CURRENT_BINARY_DIR}/qpid_messaging.pyo
  DESTINATION ${PYTHON_SITEARCH_PACKAGES})

INSTALL (FILES ${CMAKE_CURRENT_BINARY_DIR}/_qpid_messaging_python.so
  RENAME _qpid_messaging.so
  DESTINATION ${PYTHON_SITEARCH_PACKAGES})

