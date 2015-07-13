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

# Configure Visual Studio version-specific files into build tree

MACRO (select_msvc9_files)
    set (sln_proj_SOURCES
        examples/msvc9/csharp.example.drain/csharp.example.drain.csproj
        examples/msvc9/csharp.example.spout/csharp.example.spout.csproj
        examples/msvc9/csharp.map.callback.receiver/csharp.map.callback.receiver.csproj
        examples/msvc9/csharp.map.callback.sender/csharp.map.callback.sender.csproj
        msvc9/org.apache.qpid.messaging.sessionreceiver.sln
        msvc9/org.apache.qpid.messaging.sln
        src/msvc9/org.apache.qpid.messaging.vcproj
        src/sessionreceiver/msvc9/org.apache.qpid.messaging.sessionreceiver.csproj
        test/messaging.test/msvc9/messaging.test.csproj
        winsdk_sources/msvc9/winsdk_dotnet_examples.sln
        winsdk_sources/msvc9/examples/csharp.direct.receiver/csharp.direct.receiver.csproj
        winsdk_sources/msvc9/examples/csharp.direct.sender/csharp.direct.sender.csproj
        winsdk_sources/msvc9/examples/csharp.example.client/csharp.example.client.csproj
        winsdk_sources/msvc9/examples/csharp.example.declare_queues/csharp.example.declare_queues.csproj
        winsdk_sources/msvc9/examples/csharp.example.drain/csharp.example.drain.csproj
        winsdk_sources/msvc9/examples/csharp.example.helloworld/csharp.example.helloworld.csproj
        winsdk_sources/msvc9/examples/csharp.example.server/csharp.example.server.csproj
        winsdk_sources/msvc9/examples/csharp.example.spout/csharp.example.spout.csproj
        winsdk_sources/msvc9/examples/csharp.map.callback.receiver/csharp.map.callback.receiver.csproj
        winsdk_sources/msvc9/examples/csharp.map.callback.sender/csharp.map.callback.sender.csproj
        winsdk_sources/msvc9/examples/csharp.map.receiver/csharp.map.receiver.csproj
        winsdk_sources/msvc9/examples/csharp.map.sender/csharp.map.sender.csproj)

    # Base path from any example project
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_BINARY_DIR}/examples/msvc9/csharp.direct.receiver" DOTNET_src)
    # to source root
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/examples" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_exampleRelPathToSrc ${DOTNET_src} ${DOTNET_tgt})
    file(TO_NATIVE_PATH ${DOTNET_exampleRelPathToSrc} DOTNET_exampleRelPathToSrc)

    # to org.apache.qpid.messaging.dll through a project reference
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_BINARY_DIR}/src/msvc9/org.apache.qpid.messaging.vcxproj" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_exampleRelPathToBindingProj ${DOTNET_src} ${DOTNET_tgt})
    file(TO_NATIVE_PATH ${DOTNET_exampleRelPathToBindingProj} DOTNET_exampleRelPathToBindingProj)

    # to org.apache.qpid.messaging.sessionreceiver.dll through a project reference
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_BINARY_DIR}/src/sessionreceiver/msvc9/org.apache.qpid.messaging.sessionreceiver.csproj" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_exampleRelPathToSessionreceiverProj ${DOTNET_src} ${DOTNET_tgt})
    file(TO_NATIVE_PATH ${DOTNET_exampleRelPathToSessionreceiverProj} DOTNET_exampleRelPathToSessionreceiverProj)

    # to app.config file
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/examples/msvc9/app.config" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_exampleRelPathToAppConfig ${DOTNET_src} ${DOTNET_tgt})
    file(TO_NATIVE_PATH ${DOTNET_exampleRelPathToAppConfig} DOTNET_exampleRelPathToAppConfig)
ENDMACRO (select_msvc9_files)

MACRO (select_msvcx_files)
    set (sln_proj_SOURCES
        examples/msvcx/csharp.example.drain/csharp.example.drain.csproj
        examples/msvcx/csharp.example.spout/csharp.example.spout.csproj
        examples/msvcx/csharp.map.callback.receiver/csharp.map.callback.receiver.csproj
        examples/msvcx/csharp.map.callback.sender/csharp.map.callback.sender.csproj
        msvcx/org.apache.qpid.messaging.sessionreceiver.sln
        msvcx/org.apache.qpid.messaging.sln
        src/msvcx/org.apache.qpid.messaging.vcxproj
        src/sessionreceiver/msvcx/org.apache.qpid.messaging.sessionreceiver.csproj
        test/messaging.test/msvcx/messaging.test.csproj
        winsdk_sources/msvcx/winsdk_dotnet_examples.sln
        winsdk_sources/msvcx/examples/csharp.direct.receiver/csharp.direct.receiver.csproj
        winsdk_sources/msvcx/examples/csharp.direct.sender/csharp.direct.sender.csproj
        winsdk_sources/msvcx/examples/csharp.example.client/csharp.example.client.csproj
        winsdk_sources/msvcx/examples/csharp.example.declare_queues/csharp.example.declare_queues.csproj
        winsdk_sources/msvcx/examples/csharp.example.drain/csharp.example.drain.csproj
        winsdk_sources/msvcx/examples/csharp.example.helloworld/csharp.example.helloworld.csproj
        winsdk_sources/msvcx/examples/csharp.example.server/csharp.example.server.csproj
        winsdk_sources/msvcx/examples/csharp.example.spout/csharp.example.spout.csproj
        winsdk_sources/msvcx/examples/csharp.map.callback.receiver/csharp.map.callback.receiver.csproj
        winsdk_sources/msvcx/examples/csharp.map.callback.sender/csharp.map.callback.sender.csproj
        winsdk_sources/msvcx/examples/csharp.map.receiver/csharp.map.receiver.csproj
        winsdk_sources/msvcx/examples/csharp.map.sender/csharp.map.sender.csproj)

    # Base path from any example project
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_BINARY_DIR}/examples/msvcx/csharp.direct.receiver" DOTNET_src)
    # to source root
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/examples" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_exampleRelPathToSrc ${DOTNET_src} ${DOTNET_tgt})
    file(TO_NATIVE_PATH ${DOTNET_exampleRelPathToSrc} DOTNET_exampleRelPathToSrc)

    # to org.apache.qpid.messaging.dll through a project reference
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_BINARY_DIR}/src/msvcx/org.apache.qpid.messaging.vcxproj" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_exampleRelPathToBindingProj ${DOTNET_src} ${DOTNET_tgt})
    file(TO_NATIVE_PATH ${DOTNET_exampleRelPathToBindingProj} DOTNET_exampleRelPathToBindingProj)

    # to org.apache.qpid.messaging.sessionreceiver.dll through a project reference
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_BINARY_DIR}/src/sessionreceiver/msvcx/org.apache.qpid.messaging.sessionreceiver.csproj" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_exampleRelPathToSessionreceiverProj ${DOTNET_src} ${DOTNET_tgt})
    file(TO_NATIVE_PATH ${DOTNET_exampleRelPathToSessionreceiverProj} DOTNET_exampleRelPathToSessionreceiverProj)

    # to app.config file
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/examples/msvcx/app.config" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_exampleRelPathToAppConfig ${DOTNET_src} ${DOTNET_tgt})
    file(TO_NATIVE_PATH ${DOTNET_exampleRelPathToAppConfig} DOTNET_exampleRelPathToAppConfig)
ENDMACRO (select_msvcx_files)

MACRO (configure_example_project msvc9_or_x name guid)
    set(DOTNET_projectName ${name})
    set(DOTNET_projectGuid ${guid})
    configure_file(${CMAKE_CURRENT_SOURCE_DIR}/examples/${msvc9_or_x}/anyproject.csproj.in
                   ${CMAKE_CURRENT_BINARY_DIR}/examples/${msvc9_or_x}/${DOTNET_projectName}/${DOTNET_projectName}.csproj)
ENDMACRO (configure_example_project DOTNET_projectName DOTNET_projectGuid)

MACRO (configure_example_projects msvc9_or_x)
    configure_example_project(${msvc9_or_x} "csharp.direct.receiver"        "52F880E7-D677-4C91-8516-D679CE0F46A8")
    configure_example_project(${msvc9_or_x} "csharp.direct.sender"          "7B71CE78-8E78-4632-ADBE-F4D5DFAE0068")
    configure_example_project(${msvc9_or_x} "csharp.example.client"         "0DE01712-C2D1-4CA4-B42C-5856456A8696")
    configure_example_project(${msvc9_or_x} "csharp.example.declare_queues" "E31B349C-830C-4583-8BD9-30DA4398349F")
    configure_example_project(${msvc9_or_x} "csharp.example.helloworld"     "8CC1C265-0507-44A3-9483-8FAF48513F4D")
    configure_example_project(${msvc9_or_x} "csharp.example.server"         "090A081D-E8B5-4949-AA43-EE182B7101E3")
    configure_example_project(${msvc9_or_x} "csharp.map.receiver"           "AD9E53D7-DB10-4DA2-84D2-A81BE09B04E9")
    configure_example_project(${msvc9_or_x} "csharp.map.sender"             "5D8252F5-E1D3-44A0-94C7-7CB75E843C10")
ENDMACRO (configure_example_projects msvc9_or_x)

if (MSVC)
    # Source and binary folders must be on the same drive so that
    # relative paths between the two are possible.
    # Note that this is for the DOTNET binding only and does not apply to the qpid-cpp main build.
    string(SUBSTRING ${CMAKE_CURRENT_SOURCE_DIR} 0 1 srcDriveLetter)
    string(SUBSTRING ${CMAKE_CURRENT_BINARY_DIR} 0 1 binDriveLetter)
    if (NOT (${srcDriveLetter} STREQUAL ${binDriveLetter}))
        message(FATAL_ERROR "Windows DOTNET binding builds must have source and binary folders on same drive.")
    endif()

    # General top level folders
    file(TO_NATIVE_PATH ${CMAKE_CURRENT_BINARY_DIR} DOTNET_currentBinaryDir)
    file(TO_NATIVE_PATH ${CMAKE_CURRENT_SOURCE_DIR} DOTNET_currentSourceDir)
    file(TO_NATIVE_PATH ${PROJECT_BINARY_DIR}       DOTNET_projectBinaryDir)
    file(TO_NATIVE_PATH ${PROJECT_SOURCE_DIR}       DOTNET_projectSourceDir)

    # relative path from top-level binary back to top-level source
    if (${CMAKE_CURRENT_BINARY_DIR} STREQUAL ${CMAKE_CURRENT_SOURCE_DIR})
        set (DOTNET_relPathToSrc ".\\")
    else()
        file(RELATIVE_PATH DOTNET_relPathToSrc "${CMAKE_CURRENT_BINARY_DIR}" "${CMAKE_CURRENT_SOURCE_DIR}")
        file(TO_NATIVE_PATH ${DOTNET_relPathToSrc} DOTNET_relPathToSrc)
    endif()

    if(${CMAKE_GENERATOR} MATCHES "^Visual Studio 9")
        # Visual Studio 2008
        select_msvc9_files()
        set(DOTNET_MSVC_SELECT              "msvc9")
        set(DOTNET_SLN_FILE_FORMAT          "10.00")
        set(DOTNET_SLN_VISUAL_STUDIO        "Visual Studio 2008")
        set(DOTNET_TARGET_FRAMEWORK_VERSION "v3.5")
        set(DOTNET_PLATFORM_TOOLSET         "")

    elseif(${CMAKE_GENERATOR} MATCHES "^Visual Studio 10")
        # Visual Studio 2010
        select_msvcx_files()
        set(DOTNET_MSVC_SELECT              "msvcx")
        set(DOTNET_SLN_FILE_FORMAT          "11.00")
        set(DOTNET_SLN_VISUAL_STUDIO        "Visual Studio 2010")
        set(DOTNET_TARGET_FRAMEWORK_VERSION "v4.0")
        set(DOTNET_PLATFORM_TOOLSET         "")

    elseif(${CMAKE_GENERATOR} MATCHES "^Visual Studio 11")
        # Visual Studio 2012
        select_msvcx_files()
        set(DOTNET_MSVC_SELECT              "msvcx")
        set(DOTNET_SLN_FILE_FORMAT          "12.00")
        set(DOTNET_SLN_VISUAL_STUDIO        "Visual Studio 2012")
        set(DOTNET_TARGET_FRAMEWORK_VERSION "v4.5")
        set(DOTNET_PLATFORM_TOOLSET         "<PlatformToolset>v110</PlatformToolset>")

    elseif(${CMAKE_GENERATOR} MATCHES "^Visual Studio 12")
        # Visual Studio 2013
        select_msvcx_files()
        set(DOTNET_MSVC_SELECT              "msvcx")
        set(DOTNET_SLN_FILE_FORMAT          "13.00")
        set(DOTNET_SLN_VISUAL_STUDIO        "Visual Studio 2013")
        set(DOTNET_TARGET_FRAMEWORK_VERSION "v4.5")
        set(DOTNET_PLATFORM_TOOLSET         "<PlatformToolset>v120</PlatformToolset>")

    else()
        message(FATAL_ERROR "No DOTNET binding support available for ${CMAKE_GENERATOR}")
    endif()

    # Configure common examples and individual files
    configure_example_projects( ${DOTNET_MSVC_SELECT} )
    foreach(slnProjFile ${sln_proj_SOURCES})
        set (iFile "${slnProjFile}.in")
        configure_file(${CMAKE_CURRENT_SOURCE_DIR}/${iFile} ${CMAKE_CURRENT_BINARY_DIR}/${slnProjFile})
    endforeach()
endif (MSVC)
