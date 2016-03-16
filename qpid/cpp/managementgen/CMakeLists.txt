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
option(INSTALL_QMFGEN "Install QMF-GEN and QMF Python site-packages" ON)

if (INSTALL_QMFGEN)
    project(qpidc-qmfgen)
    cmake_minimum_required(VERSION 2.8.7 FATAL_ERROR)

    find_package(PythonInterp REQUIRED)

    execute_process(COMMAND ${PYTHON_EXECUTABLE}
                    -c "from distutils.sysconfig import get_python_lib; print get_python_lib(False, prefix='${CMAKE_INSTALL_PREFIX}').replace('\\\\', '/')"
                    OUTPUT_VARIABLE PYTHON_SITEARCH_PACKAGES
                    OUTPUT_STRIP_TRAILING_WHITESPACE)

    install(PROGRAMS qmf-gen DESTINATION ${QPID_INSTALL_BINDIR}
            COMPONENT ${QPID_COMPONENT_QMF})
    install(DIRECTORY qmfgen
            DESTINATION ${PYTHON_SITEARCH_PACKAGES}
            COMPONENT ${QPID_COMPONENT_QMF}
            PATTERN ".svn" EXCLUDE PATTERN "*.pyc" EXCLUDE)
else (INSTALL_QMFGEN)
    message (STATUS "Skipping installing qmf-gen and qmf python site-packages")
endif (INSTALL_QMFGEN)
