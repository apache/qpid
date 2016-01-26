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

include(FindPackageHandleStandardArgs)
include(FindPackageMessage)

# First try to find the Installed Proton config (Proton 0.7 and later)
find_package(Proton QUIET NO_MODULE)
if (Proton_FOUND)
    find_package_message(Proton "Found Proton: ${Proton_LIBRARIES} (found version \"${Proton_VERSION}\")" "$Proton_DIR ${Proton_LIBRARIES} $Proton_VERSION")
    return()
endif ()

# Now look for the cooky Proton config installed with some earlier
# versions of Proton
find_package(proton QUIET NO_MODULE)
if (proton_FOUND)
    include("${proton_DIR}/libqpid-proton.cmake")
    set (Proton_VERSION ${PROTON_VERSION})
    set (Proton_INCLUDE_DIRS ${PROTON_INCLUDE_DIRS})
    set (Proton_LIBRARIES ${PROTON_LIBRARIES})
    set (Proton_FOUND true)
    find_package_message(Proton "Found Proton: ${Proton_LIBRARIES} (found version \"${Proton_VERSION}\")" "$Proton_DIR ${Proton_LIBRARIES} $Proton_VERSION")
    return()
endif ()

# Now look for any pkg-config configuration
find_package(PkgConfig QUIET)

if (PKG_CONFIG_FOUND)
    # Check for cmake 2.6
    if (NOT ${CMAKE_VERSION} VERSION_LESS "2.8.0")
        set (FindPkgQUIET QUIET)
    endif()

    if (NOT Proton_FIND_VERSION)
        pkg_check_modules(Proton ${FindPkgQUIET} libqpid-proton)
    elseif(NOT Proton_FIND_VERSION_EXACT)
        pkg_check_modules(Proton ${FindPkgQUIET} libqpid-proton>=${Proton_FIND_VERSION})
    else()
        pkg_check_modules(Proton ${FindPkgQUIET} libqpid-proton=${Proton_FIND_VERSION})
    endif()
    if (Proton_FOUND)
        find_library(Proton_LIBRARY ${Proton_LIBRARIES} HINTS ${Proton_LIBRARY_DIRS})
        set (Proton_LIBRARIES ${Proton_LIBRARY})
        find_package_message(Proton "Found Proton: ${Proton_LIBRARIES} (found version \"${Proton_VERSION}\")" "$Proton_DIR ${Proton_LIBRARIES} $Proton_VERSION")
        return()
    endif ()
endif()

# Allow ccmake or command-line to set checked out but not installed Proton location
# Defaule location is ${HOME}/qpid-proton
set(Proton_CHECKOUT_DIR "$ENV{HOME}/qpid-proton" CACHE PATH "Proton checkout directory")
set(Proton_BUILD_DIR_NAME "build" CACHE STRING "Proton build directory name within Proton_CHECKOUT_DIR")
if (EXISTS ${Proton_CHECKOUT_DIR}/${Proton_BUILD_DIR_NAME}/proton-c/libqpid-proton.so)
    include("${Proton_CHECKOUT_DIR}/${Proton_BUILD_DIR_NAME}/proton-c/ProtonConfig.cmake")
    set (Proton_INCLUDE_DIRS "${Proton_CHECKOUT_DIR}/proton-c/include" "${Proton_CHECKOUT_DIR}/${Proton_BUILD_DIR_NAME}/proton-c/include")
    set (Proton_LIBRARIES "${Proton_CHECKOUT_DIR}/${Proton_BUILD_DIR_NAME}/proton-c/libqpid-proton.so")
    find_package_message(Proton "Found uninstalled Proton: ${Proton_LIBRARIES} (found version \"${Proton_VERSION}\")" "$ProtonX_DIR ${Proton_LIBRARIES} $Proton_VERSION")
    return()
endif ()

# Proton not found print a standard error message
if (NOT ${CMAKE_VERSION} VERSION_LESS "2.8.3")
    find_package_handle_standard_args(Proton CONFIG_MODE)
endif()
