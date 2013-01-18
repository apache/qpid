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

install (FILES
           ${CMAKE_CURRENT_SOURCE_DIR}/extra_dist/CMakeLists.txt
           ${CMAKE_CURRENT_SOURCE_DIR}/OptionParser.cpp
           ${CMAKE_CURRENT_SOURCE_DIR}/OptionParser.h
           ${CMAKE_CURRENT_SOURCE_DIR}/hello_world.cpp
           ${CMAKE_CURRENT_SOURCE_DIR}/hello_xml.cpp
           ${CMAKE_CURRENT_SOURCE_DIR}/drain.cpp
           ${CMAKE_CURRENT_SOURCE_DIR}/spout.cpp
           ${CMAKE_CURRENT_SOURCE_DIR}/map_receiver.cpp
           ${CMAKE_CURRENT_SOURCE_DIR}/map_sender.cpp
           ${CMAKE_CURRENT_SOURCE_DIR}/client.cpp
           ${CMAKE_CURRENT_SOURCE_DIR}/server.cpp
           DESTINATION ${QPID_INSTALL_EXAMPLESDIR}/messaging
           COMPONENT ${QPID_COMPONENT_EXAMPLES})

