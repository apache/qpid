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

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Duration is a time interval in milliseconds.
    /// It is a managed equivalent of ::qpid::messaging::Duration
    /// </summary>

    public ref class Duration sealed
    {
    private:
        System::UInt64 milliseconds;

    public:

        Duration(const Duration % rhs)
            : milliseconds(rhs.milliseconds) {}

        explicit Duration(System::UInt64 mS)
            : milliseconds(mS) {}

        Duration()
            : milliseconds(System::UInt64::MaxValue) {}

        property System::UInt64 Milliseconds
        {
            System::UInt64 get () { return milliseconds; }
        }

        static Duration ^ operator * (Duration ^ dur, const System::UInt64 multiplier)
        {
            Duration ^ result = gcnew Duration(dur->Milliseconds * multiplier);
            return result;
        }

        static Duration ^ operator * (const System::UInt64 multiplier, Duration ^ dur)
        {
            Duration ^ result = gcnew Duration(multiplier * dur->Milliseconds);
            return result;
        }

        static Duration ^ Multiply (Duration ^ dur, const System::UInt64 multiplier)
        {
            Duration ^ result = gcnew Duration(dur->Milliseconds * multiplier);
            return result;
        }

        static Duration ^ Multiply (const System::UInt64 multiplier, Duration ^ dur)
        {
            Duration ^ result = gcnew Duration(multiplier * dur->Milliseconds);
            return result;
        }

        static bool operator == (Duration ^ a, Duration ^ b)
        {
            return a->Milliseconds == b->Milliseconds;
        }

        static bool operator != (Duration ^ a, Duration ^ b)
        {
            return a->Milliseconds != b->Milliseconds;
        }
    };

    public ref class DurationConstants sealed
    {
    private:
        DurationConstants::DurationConstants() {}

    public:
        static Duration ^ FORVER;
        static Duration ^ IMMEDIATE;
        static Duration ^ SECOND;
        static Duration ^ MINUTE;

        static DurationConstants()
        {
            FORVER    = gcnew Duration();
            IMMEDIATE = gcnew Duration(0);
            SECOND    = gcnew Duration(1000);
            MINUTE    = gcnew Duration(60000);
        }
    };
}}}}
