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

#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Session.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Connection is a managed wrapper for a qpid::messaging::Connection
    /// </summary>

    ref class Session;

    public ref class Connection
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Connection * connectionp;

        // Kept object deletion code
        void Cleanup();

    public:
        Connection(System::String ^ url);

        Connection(System::String ^ url, 
                   System::Collections::Generic::Dictionary<
                       System::String ^, System::Object ^> ^ options);

        Connection(System::String ^ url, System::String ^ options);
        ~Connection();
        !Connection();

        void setOption(System::String ^ name, System::Object ^ value);

        void open();
        System::Boolean isOpen();
        void close();

        // createTransactionalSession()
        Session ^ createTransactionalSession();
        Session ^ createTransactionalSession(System::String ^ name);

        // createSession()
        Session ^ createSession();
        Session ^ createSession(System::String ^ name);

        Session ^ getSession(System::String ^ name);
    };
}}}}
