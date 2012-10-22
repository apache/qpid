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

# Optional AMQP1.0 support. Requires proton toolkit.

include(FindPkgConfig)

pkg_check_modules(PROTON libqpid-proton)

set (amqp_default ${amqp_force})
if (PROTON_FOUND)
    message(STATUS "Qpid proton found, amqp 1.0 support enabled")
    set (amqp_default ON)
else (PROTON_FOUND)
    message(STATUS "Qpid proton not found, amqp 1.0 support not enabled")
endif (PROTON_FOUND)

option(BUILD_AMQP "Build with support for AMQP 1.0" ${amqp_default})
if (BUILD_AMQP)

    if (NOT PROTON_FOUND)
      message(FATAL_ERROR "Qpid proton not found, required for amqp 1.0 support")
    endif (NOT PROTON_FOUND)

    foreach(f ${PROTON_CFLAGS})
      set (PROTON_COMPILE_FLAGS "${PROTON_COMPILE_FLAGS} ${f}")
    endforeach(f)

    foreach(f ${PROTON_LDFLAGS})
      set (PROTON_LINK_FLAGS "${PROTON_LINK_FLAGS} ${f}")
    endforeach(f)


    set (amqp_SOURCES
         qpid/broker/amqp/Connection.h
         qpid/broker/amqp/Connection.cpp
         qpid/broker/amqp/Header.h
         qpid/broker/amqp/Header.cpp
         qpid/broker/amqp/ManagedConnection.h
         qpid/broker/amqp/ManagedConnection.cpp
         qpid/broker/amqp/ManagedSession.h
         qpid/broker/amqp/ManagedSession.cpp
         qpid/broker/amqp/ManagedOutgoingLink.h
         qpid/broker/amqp/ManagedOutgoingLink.cpp
         qpid/broker/amqp/Message.h
         qpid/broker/amqp/Message.cpp
         qpid/broker/amqp/Outgoing.h
         qpid/broker/amqp/Outgoing.cpp
         qpid/broker/amqp/ProtocolPlugin.cpp
         qpid/broker/amqp/Sasl.h
         qpid/broker/amqp/Sasl.cpp
         qpid/broker/amqp/Session.h
         qpid/broker/amqp/Session.cpp
         qpid/broker/amqp/Translation.h
         qpid/broker/amqp/Translation.cpp
        )
    add_library (amqp MODULE ${amqp_SOURCES})
    target_link_libraries (amqp qpidbroker qpidcommon)
    set_target_properties (amqp PROPERTIES
                           PREFIX ""
                           COMPILE_FLAGS "${PROTON_COMPILE_FLAGS}"
                           LINK_FLAGS "${PROTON_LINK_FLAGS}")
    install (TARGETS amqp
             DESTINATION ${QPIDD_MODULE_DIR}
             COMPONENT ${QPID_COMPONENT_BROKER})


    set (amqpc_SOURCES
         qpid/messaging/amqp/ConnectionContext.h
         qpid/messaging/amqp/ConnectionContext.cpp
         qpid/messaging/amqp/ConnectionHandle.h
         qpid/messaging/amqp/ConnectionHandle.cpp
         qpid/messaging/amqp/DriverImpl.h
         qpid/messaging/amqp/DriverImpl.cpp
         qpid/messaging/amqp/ReceiverContext.h
         qpid/messaging/amqp/ReceiverContext.cpp
         qpid/messaging/amqp/ReceiverHandle.h
         qpid/messaging/amqp/ReceiverHandle.cpp
         qpid/messaging/amqp/Sasl.h
         qpid/messaging/amqp/Sasl.cpp
         qpid/messaging/amqp/SenderContext.h
         qpid/messaging/amqp/SenderContext.cpp
         qpid/messaging/amqp/SenderHandle.h
         qpid/messaging/amqp/SenderHandle.cpp
         qpid/messaging/amqp/SessionContext.h
         qpid/messaging/amqp/SessionContext.cpp
         qpid/messaging/amqp/SessionHandle.h
         qpid/messaging/amqp/SessionHandle.cpp
         qpid/messaging/amqp/TcpTransport.h
         qpid/messaging/amqp/TcpTransport.cpp
        )
    add_library (amqpc MODULE ${amqpc_SOURCES})
    target_link_libraries (amqpc qpidclient qpidcommon)
    set_target_properties (amqpc PROPERTIES
                           PREFIX ""
                           COMPILE_FLAGS "${PROTON_COMPILE_FLAGS}"
                           LINK_FLAGS "${PROTON_LINK_FLAGS}")
    install (TARGETS amqpc
             DESTINATION ${QPIDC_MODULE_DIR}
             COMPONENT ${QPID_COMPONENT_CLIENT})

endif (BUILD_AMQP)
