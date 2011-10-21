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

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Connection is a managed wrapper for a qpid::messaging::Connection
    /// </summary>

    ref class Session;

    public ref class Connection
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Connection * connectionp;

    public:
        Connection(System::String ^ url);

        Connection(System::String ^ url, 
                   System::Collections::Generic::Dictionary<
                       System::String ^, System::Object ^> ^ options);

        Connection(System::String ^ url, System::String ^ options);

        // copy constructor
        Connection(const Connection ^ connection);
        Connection(const Connection % connection);

		// unmanaged clone
		// not defined

        ~Connection();
        !Connection();

        // assignment operator
        Connection % operator=(const Connection % rhs)
        {
            if (this == %rhs)
            {
                // Self assignment, do nothing
            }
            else
            {
                if (NULL != connectionp)
                    delete connectionp;
                connectionp = new ::qpid::messaging::Connection(
                    *(const_cast<Connection %>(rhs).NativeConnection) );
            }
            return *this;
        }

        property ::qpid::messaging::Connection * NativeConnection
        {
            ::qpid::messaging::Connection * get () { return connectionp; }
        }

        void SetOption(System::String ^ name, System::Object ^ value);

        void Open();
        void Close();

        property System::Boolean IsOpen
        {
            System::Boolean get ()
            {
                return connectionp->isOpen();
            }
        }

        // CreateTransactionalSession()
        Session ^ CreateTransactionalSession();
        Session ^ CreateTransactionalSession(System::String ^ name);

        // CreateSession()
        Session ^ CreateSession();
        Session ^ CreateSession(System::String ^ name);

        Session ^ GetSession(System::String ^ name);

        property System::String ^ AuthenticatedUsername
        {
            System::String ^ get ()
            {
                return gcnew System::String(connectionp->getAuthenticatedUsername().c_str());
            }
        }
    };
}}}}
