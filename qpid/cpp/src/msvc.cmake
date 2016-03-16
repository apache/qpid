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

# This file provides library support for MSVC builds.
#  * Allows for detailed specification of file/product versions.
#  * Installs PDB files.

#
# If the compiler is Visual Studio set up installation of .pdb files 
#
# Sample: install_pdb (qpidcommon ${QPID_COMPONENT_COMMON})
#
MACRO (install_pdb theLibrary theComponent)
    if (MSVC)
        install (FILES
                $<TARGET_PDB_FILE:${theLibrary}>
                DESTINATION ${QPID_INSTALL_LIBDIR}/ReleasePDB
                COMPONENT ${theComponent}
                OPTIONAL
                CONFIGURATIONS Release MinSizeRel RelWithDebInfo)
        install (FILES
                $<TARGET_PDB_FILE:${theLibrary}>
                DESTINATION ${QPID_INSTALL_LIBDIR}/DebugPDB
                COMPONENT ${theComponent}
                CONFIGURATIONS Debug)
    endif (MSVC)
ENDMACRO (install_pdb)

#
# inherit_value - if the symbol is undefined then set it to the given value.
# Set flag to indicate this symbol was defined here.
#
MACRO (inherit_value theSymbol theValue)
    if (NOT DEFINED ${theSymbol})
        set (${theSymbol} ${theValue})
        # message ("Set symbol '${theSymbol}' to value '${theValue}'")
        set (${theSymbol}_inherited = "true")
    endif (NOT DEFINED ${theSymbol})
ENDMACRO (inherit_value)

#
# If compiler is Visual Studio then create a "version resource" for the project.
# Use this call to override CPACK and file global settings but not file per-project settings.
# Two groups of four version numbers specify "file" and "product" versions separately.
#
# Sample: add_msvc_version_full (qmfengine library dll 1 0 0 1 1 0 0 1)
#
MACRO (add_msvc_version_full verProject verProjectType verProjectFileExt verFN1 verFN2 verFN3 verFN4 verPN1 verPN2 verPN3 verPN4)
    if (MSVC)
        # Create project-specific version strings
        inherit_value ("winver_${verProject}_FileVersionBinary"    "${verFN1},${verFN2},${verFN3},${verFN4}")
        inherit_value ("winver_${verProject}_ProductVersionBinary" "${verPN1},${verPN2},${verPN3},${verPN4}")
        inherit_value ("winver_${verProject}_FileVersionString"    "${verFN1}, ${verFN2}, ${verFN3}, ${verFN4}")
        inherit_value ("winver_${verProject}_ProductVersionString" "${verPN1}, ${verPN2}, ${verPN3}, ${verPN4}")
        inherit_value ("winver_${verProject}_FileDescription"      "${winver_PACKAGE_NAME}-${verProject} ${verProjectType}")
        inherit_value ("winver_${verProject}_LegalCopyright"       "${winver_LEGAL_COPYRIGHT}")
        inherit_value ("winver_${verProject}_InternalName"         "${verProject}")
        inherit_value ("winver_${verProject}_OriginalFilename"     "${verProject}.${verProjectFileExt}")
        inherit_value ("winver_${verProject}_ProductName"          "${winver_DESCRIPTION_SUMMARY}")

        # Create strings to be substituted into the template file
        set ("winverFileVersionBinary"     "${winver_${verProject}_FileVersionBinary}")
        set ("winverProductVersionBinary"  "${winver_${verProject}_ProductVersionBinary}")
        set ("winverFileVersionString"     "${winver_${verProject}_FileVersionString}")
        set ("winverProductVersionString"  "${winver_${verProject}_ProductVersionString}")
        set ("winverFileDescription"       "${winver_${verProject}_FileDescription}")
        set ("winverLegalCopyright"        "${winver_${verProject}_LegalCopyright}")
        set ("winverInternalName"          "${winver_${verProject}_InternalName}")
        set ("winverOriginalFilename"      "${winver_${verProject}_OriginalFilename}")
        set ("winverProductName"           "${winver_${verProject}_ProductName}")

        configure_file(${CMAKE_CURRENT_SOURCE_DIR}/windows/resources/template-resource.rc
                       ${CMAKE_CURRENT_BINARY_DIR}/windows/resources/${verProject}-resource.rc)
        set (${verProject}_SOURCES
            ${${verProject}_SOURCES}
            ${CMAKE_CURRENT_BINARY_DIR}/windows/resources/${verProject}-resource.rc
        )
    endif (MSVC)
ENDMACRO (add_msvc_version_full)

#
# If compiler is Visual Studio then create a "version resource" for the project.
# Use this call to accept file override version settings or
#  inherited CPACK_PACKAGE_VERSION version settings.
#
# Sample: add_msvc_version (qpidcommon library dll)
#
MACRO (add_msvc_version verProject verProjectType verProjectFileExt)
    if (MSVC)
        add_msvc_version_full (${verProject}
                               ${verProjectType}
                               ${verProjectFileExt}
                               ${winver_FILE_VERSION_N1}
                               ${winver_FILE_VERSION_N2}
                               ${winver_FILE_VERSION_N3}
                               ${winver_FILE_VERSION_N4}
                               ${winver_PRODUCT_VERSION_N1}
                               ${winver_PRODUCT_VERSION_N2}
                               ${winver_PRODUCT_VERSION_N3}
                               ${winver_PRODUCT_VERSION_N4})
    endif (MSVC)
ENDMACRO (add_msvc_version)

#
# Install optional windows version settings. Override variables are specified in a file.
#
include (./CMakeWinVersions.cmake OPTIONAL)

#
# Inherit global windows version settings from CPACK settings.
#
inherit_value ("winver_PACKAGE_NAME"         "${CPACK_PACKAGE_NAME}")
inherit_value ("winver_DESCRIPTION_SUMMARY"  "${CPACK_PACKAGE_DESCRIPTION_SUMMARY}")
inherit_value ("winver_FILE_VERSION_N1"      "${CPACK_PACKAGE_VERSION_MAJOR}")
inherit_value ("winver_FILE_VERSION_N2"      "${CPACK_PACKAGE_VERSION_MINOR}")
inherit_value ("winver_FILE_VERSION_N3"      "${CPACK_PACKAGE_VERSION_PATCH}")
inherit_value ("winver_FILE_VERSION_N4"      "1")
inherit_value ("winver_PRODUCT_VERSION_N1"   "${winver_FILE_VERSION_N1}")
inherit_value ("winver_PRODUCT_VERSION_N2"   "${winver_FILE_VERSION_N2}")
inherit_value ("winver_PRODUCT_VERSION_N3"   "${winver_FILE_VERSION_N3}")
inherit_value ("winver_PRODUCT_VERSION_N4"   "${winver_FILE_VERSION_N4}")
inherit_value ("winver_LEGAL_COPYRIGHT"      "")
