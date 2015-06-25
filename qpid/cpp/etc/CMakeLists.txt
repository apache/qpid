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

if (UNIX)

  # Use absolute paths as these are substituted into init scripts.
  set_absolute_install_path (bindir ${QPID_INSTALL_BINDIR})
  set_absolute_install_path (sysconfdir ${SYSCONF_INSTALL_DIR})
  set_absolute_install_path (sbindir ${QPID_INSTALL_SBINDIR})
  set_absolute_install_path (initdir ${QPID_INSTALL_INITDDIR})
  set_absolute_install_path (confdir ${QPID_INSTALL_CONFDIR})

  configure_file(qpidd.in
    ${CMAKE_CURRENT_BINARY_DIR}/qpidd
    @ONLY)

  configure_file(qpidd-primary.in
    ${CMAKE_CURRENT_BINARY_DIR}/qpidd-primary
    @ONLY)

install(FILES ${CMAKE_CURRENT_BINARY_DIR}/qpidd ${CMAKE_CURRENT_BINARY_DIR}/qpidd-primary
        PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE
        DESTINATION ${QPID_INSTALL_INITDDIR}
        COMPONENT ${QPID_COMPONENT_BROKER})
endif (UNIX)

install(FILES qpidc.conf
        DESTINATION ${QPID_INSTALL_CONFDIR}
        COMPONENT ${QPID_COMPONENT_CLIENT})

install(FILES qpidd.conf
        DESTINATION ${QPID_INSTALL_CONFDIR}
        COMPONENT ${QPID_COMPONENT_BROKER})

if (BUILD_SASL)
  install(FILES sasl2/qpidd.conf
          DESTINATION ${QPID_INSTALL_SASLDIR}
          COMPONENT ${QPID_COMPONENT_BROKER}
          RENAME ${QPID_BROKER_SASL_NAME}.conf)
endif (BUILD_SASL)

