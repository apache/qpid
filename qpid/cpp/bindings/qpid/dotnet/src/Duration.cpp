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

#include <windows.h>
#include <msclr\lock.h>
#include <oletx2xa.h>
#include <string>
#include <limits>

#include "qpid/messaging/Duration.h"

#include "Duration.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Duration is a time interval in milliseconds.
    /// It is a managed wrapper for a ::qpid::messaging::Duration
    /// </summary>

    // Public constructor
    Duration::Duration(System::UInt64 milliseconds) :
        durationp(new ::qpid::messaging::Duration(milliseconds))
    {
    }

    // Private experimental constructor
    Duration::Duration(const ::qpid::messaging::Duration * d) :
        durationp(d)
    {
    }


    // Destructor
    Duration::~Duration()
    {
        Cleanup();
    }


    // Finalizer
    Duration::!Duration()
    {
        Cleanup();
    }


    // Returns the value from kept object
    System::UInt64 Duration::getMilliseconds()
    {
        return durationp->getMilliseconds();
    }


    // Destroys kept object
    // TODO: add lock
    void Duration::Cleanup()
    {
        if (NULL != durationp)
        {
            delete durationp;
            durationp = NULL;
        }
    }

    // Return value(s) for constant durations
    // NOTE: These return the duration mS and not a Duration 
    //       object like the C++ code gets.
    System::UInt64 Duration::FOREVER()
    {
        return ::qpid::messaging::Duration::FOREVER.getMilliseconds();
    }

    System::UInt64 Duration::IMMEDIATE()
    {
        return ::qpid::messaging::Duration::IMMEDIATE.getMilliseconds();
    }

    System::UInt64 Duration::SECOND()
    {
        return ::qpid::messaging::Duration::SECOND.getMilliseconds();
    }

    System::UInt64 Duration::MINUTE()
    {
        return ::qpid::messaging::Duration::MINUTE.getMilliseconds();
    }
}}}}
