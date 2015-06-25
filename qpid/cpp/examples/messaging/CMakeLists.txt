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

# drain and spout have explicit Boost.program_options usage in them, so be
# sure that lib is linked in.

macro(add_messaging_example example)
  add_executable(${example} ${example}.cpp OptionParser.cpp)
  set_target_properties(${example} PROPERTIES OUTPUT_NAME ${example})
  target_link_libraries(${example} qpidmessaging qpidtypes ${_boost_libs_needed})
  # For installs, don't install the built example; that would be pointless.
  # Install the things a user needs to build the example on-site.
  install (FILES
    ${CMAKE_CURRENT_SOURCE_DIR}/${example}.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/OptionParser.h
    ${CMAKE_CURRENT_SOURCE_DIR}/OptionParser.cpp
    DESTINATION ${QPID_INSTALL_EXAMPLESDIR}/messaging
    COMPONENT ${QPID_COMPONENT_EXAMPLES})

  if (MSVC)
    install (FILES ${CMAKE_CURRENT_SOURCE_DIR}/messaging_${example}.vcproj
             DESTINATION ${QPID_INSTALL_EXAMPLESDIR}/messaging
             COMPONENT ${QPID_COMPONENT_EXAMPLES})
  endif (MSVC)

endmacro(add_messaging_example)

add_messaging_example(drain)
add_messaging_example(spout)

add_messaging_example(map_receiver)
add_messaging_example(map_sender)

add_messaging_example(client)
add_messaging_example(server)
add_messaging_example(server_reconnect)

# These don't need Boost or OptionParser
add_executable(hello_world hello_world.cpp)
set_target_properties(hello_world PROPERTIES OUTPUT_NAME hello_world)
target_link_libraries(hello_world qpidmessaging qpidtypes)
install (FILES ${CMAKE_CURRENT_SOURCE_DIR}/hello_world.cpp
           DESTINATION ${QPID_INSTALL_EXAMPLESDIR}/messaging
           COMPONENT ${QPID_COMPONENT_EXAMPLES})

add_executable(hello_xml hello_xml.cpp)
set_target_properties(hello_xml PROPERTIES OUTPUT_NAME hello_xml)
target_link_libraries(hello_xml qpidmessaging qpidtypes)

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
           ${CMAKE_CURRENT_SOURCE_DIR}/server_reconnect.cpp
           DESTINATION ${QPID_INSTALL_EXAMPLESDIR}/messaging
           COMPONENT ${QPID_COMPONENT_EXAMPLES})

