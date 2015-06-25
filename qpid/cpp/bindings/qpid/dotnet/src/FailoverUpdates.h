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
    /// FailoverUpdates is a managed wrapper for a qpid::messaging::FailoverUpdates
    /// </summary>

    ref class Connection;

    public ref class FailoverUpdates
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::FailoverUpdates * nativeObjPtr;

        // per-instance lock object
        System::Object ^ privateLock;

        // Disallow use after object is destroyed
        void ThrowIfDisposed();

    public:
        FailoverUpdates(Connection ^ connection);

        ~FailoverUpdates();
        !FailoverUpdates();

        //
        // IsDisposed
        //
        property bool IsDisposed
        {
            bool get()
            {
                return NULL == nativeObjPtr;
            }
        }

    private:
        // unmanaged clone
        // not defined

        // copy constructor
        FailoverUpdates(const FailoverUpdates ^ failoverUpdates) {}
        FailoverUpdates(const FailoverUpdates % failoverUpdates) {}

        // assignment operator
        FailoverUpdates % operator=(const FailoverUpdates % rhs)
        {
            return *this;
        }
    };
}}}}
