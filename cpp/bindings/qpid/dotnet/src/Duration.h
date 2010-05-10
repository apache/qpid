/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

#pragma once

#include <windows.h>
#include <msclr\lock.h>
#include <oletx2xa.h>
#include <string>
#include <limits>

#include "qpid/messaging/Duration.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Duration is a time interval in milliseconds.
    /// It is a managed wrapper for a ::qpid::messaging::Duration
    /// </summary>

    public ref class Duration
    {
    private:
        // Experimental constructor
        Duration(const ::qpid::messaging::Duration *);

        // Kept object deletion code
        void Cleanup();

    public:
        Duration(System::UInt64 milliseconds);
        ~Duration();
        !Duration();

        // The kept object in the Messaging C++ DLL
        const ::qpid::messaging::Duration * durationp;

        System::UInt64 getMilliseconds();

        // Return value(s) for constant durations
        // NOTE: These return the duration mS and not a Duration 
        //       object like the C++ code gets.
        System::UInt64 FOREVER();
        System::UInt64 IMMEDIATE();
        System::UInt64 SECOND();
        System::UInt64 MINUTE();
    };
}}}}
