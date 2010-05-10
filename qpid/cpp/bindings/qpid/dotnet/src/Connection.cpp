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

#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Session.h"

#include "QpidMarshal.h"
#include "Connection.h"
#include "Session.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Connection is a managed wrapper for a qpid::messaging::Connection
    /// </summary>

    // Public constructor
    Connection::Connection(System::String ^ url) :
        connectionp(new ::qpid::messaging::Connection(QpidMarshal::ToNative(url)))
    {
    }

    Connection::Connection(System::String ^ url, System::String ^ options) :
        connectionp(new ::qpid::messaging::Connection(QpidMarshal::ToNative(url),
                    QpidMarshal::ToNative(options)))
    {
    }


    // Destructor
    Connection::~Connection()
    {
        Cleanup();
    }


    // Finalizer
    Connection::!Connection()
    {
        Cleanup();
    }


    // Destroys kept object
    // TODO: add lock
    void Connection::Cleanup()
    {
        if (NULL != connectionp)
        {
            delete connectionp;
            connectionp = NULL;
        }
    }

    Session ^ Connection::createSession()
    {
        return createSession("");
    }


    Session ^ Connection::createSession(System::String ^ name)
    {
        // allocate native session
        ::qpid::messaging::Session * sessionp = new ::qpid::messaging::Session;

        // create native session
        *sessionp = connectionp->createSession(QpidMarshal::ToNative(name));

        // create managed session
        Session ^ newSession = gcnew Session(sessionp, this);

        return newSession;
    }


    void Connection::open()
    {
        connectionp->open();
    }

    bool Connection::isOpen()
    {
        return connectionp->isOpen();
    }

    void Connection::close()
    {
        connectionp->close();
    }
}}}}
