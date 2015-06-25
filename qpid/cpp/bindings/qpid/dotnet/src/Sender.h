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

#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Message.h"

namespace qpid {
namespace messaging {
    // Dummy class to satisfy linker
    class SenderImpl {};
}}

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Sender is a managed wrapper for a ::qpid::messaging::Sender
    /// </summary>

    ref class Address;
    ref class Session;
    ref class Message;

    public ref class Sender
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Sender * nativeObjPtr;

        // The session that created this Sender
        Session ^ parentSession;

        // per-instance lock object
        System::Object ^ privateLock;

        // Disallow use after object is destroyed
        void ThrowIfDisposed();

    public:
        // unmanaged clone
        Sender(const ::qpid::messaging::Sender & s,
            Session ^ sessRef);

        // copy constructor
        Sender(const Sender ^ sender);
        Sender(const Sender % sender);

        ~Sender();
        !Sender();

        // assignment operator
        Sender % operator=(const Sender % rhs)
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
                nativeObjPtr = new ::qpid::messaging::Sender(
                    *(const_cast<Sender %>(rhs).NativeSender));
                parentSession = rhs.parentSession;
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
        // NativeSender
        //
        property ::qpid::messaging::Sender * NativeSender
        {
            ::qpid::messaging::Sender * get ()
            {
                return nativeObjPtr;
            }
        }


        // Send(message)
        void Send(Message ^ mmsgp);
        void Send(Message ^ mmsgp, bool sync);

        void Close();

        property System::UInt32 Capacity
        {
            System::UInt32 get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getCapacity();
            }
            void set (System::UInt32 capacity)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setCapacity(capacity);
            }
        }

        property System::UInt32 Unsettled
        {
            System::UInt32 get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getUnsettled();
            }
        }

        property System::UInt32 Available
        {
            System::UInt32 get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getAvailable();
            }
        }

        property System::String ^ Name
        {
            System::String ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return gcnew System::String(nativeObjPtr->getName().c_str());
            }
        }

        //
        // Session
        //
        property Org::Apache::Qpid::Messaging::Session ^ Session
        {
            Org::Apache::Qpid::Messaging::Session ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return parentSession;
            }
        }

        //
        // Address
        //
        Org::Apache::Qpid::Messaging::Address ^ GetAddress();
    };
}}}}
