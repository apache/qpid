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

#include "qpid/messaging/Session.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"

namespace qpid {
namespace messaging {
    // Dummy class to satisfy linker
    class SessionImpl {};
}}

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Session is a managed wrapper for a ::qpid::messaging::Session
    /// </summary>

    ref class Address;
    ref class Connection;
    ref class Duration;
    ref class Receiver;
    ref class Sender;
    ref class Message;

    public ref class Session
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Session * nativeObjPtr;

        // The connection that created this session
        Connection ^ parentConnectionp;

        // per-instance lock object
        System::Object ^ privateLock;

        // Disallow use after object is destroyed
        void ThrowIfDisposed();

    public:

        // unmanaged clone
        Session(const ::qpid::messaging::Session & nativeObjPtr,
            Connection ^ connRef);

        // copy constructor
        Session(const Session ^ session);
        Session(const Session % session);

        ~Session();
        !Session();

        // assignment operator
        Session % operator=(const Session % rhs)
        {
            msclr::lock lk(privateLock);
            ThrowIfDisposed();

            if (this == %rhs)
            {
                // Self assignment, do nothing
            }
            else
            {
                if (NULL != nativeObjPtr)
                    delete nativeObjPtr;
                nativeObjPtr = new ::qpid::messaging::Session(
                    *(const_cast<Session %>(rhs).NativeSession) );
                parentConnectionp = rhs.parentConnectionp;
            }
            return *this;
        }

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


        //
        // NativeSession
        //
        property ::qpid::messaging::Session * NativeSession
        {
            ::qpid::messaging::Session * get ()
            {
                return nativeObjPtr;
            }
        }

        void Close();
        void Commit();
        void Rollback();
        void Acknowledge();
        void Acknowledge(bool sync);
        void Acknowledge(Message ^ message);
        void Acknowledge(Message ^ message, bool sync);
        void AcknowledgeUpTo(Message ^ message);
        void AcknowledgeUpTo(Message ^ message, bool sync);
        void Reject(Message ^);
        void Release(Message ^);
        void Sync();
        void Sync(bool block);

        property System::UInt32 Receivable
        {
            System::UInt32 get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getReceivable();
            }
        }

        property System::UInt32 UnsettledAcks
        {
            System::UInt32 get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getUnsettledAcks();
            }
        }

        // next(receiver)
        bool NextReceiver(Receiver ^ rcvr);
        bool NextReceiver(Receiver ^ rcvr, Duration ^ timeout);

        // receiver = next()
        Receiver ^ NextReceiver();
        Receiver ^ NextReceiver(Duration ^ timeout);


        Sender   ^ CreateSender(System::String ^ address);
        Sender   ^ CreateSender(Address ^ address);

        Receiver ^ CreateReceiver(System::String ^ address);
        Receiver ^ CreateReceiver(Address ^ address);

        Sender   ^ GetSender(System::String ^ name);
        Receiver ^ GetReceiver(System::String ^ name);

        property Org::Apache::Qpid::Messaging::Connection ^ Connection
        {
            Org::Apache::Qpid::Messaging::Connection ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return parentConnectionp;
            }
        }


        property System::Boolean HasError
        {
            System::Boolean get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->hasError();
            }
        }

        void CheckError();
    };
}}}}
