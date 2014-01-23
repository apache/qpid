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

#include "qpid/messaging/Address.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Message.h"

#include "Sender.h"
#include "Address.h"
#include "Message.h"
#include "QpidException.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Sender a managed wrapper for a ::qpid::messaging::Sender
    /// </summary>

    // Disallow access if object has been destroyed.
    void Sender::ThrowIfDisposed()
    {
        if (IsDisposed)
            throw gcnew ObjectDisposedException (GetType()->FullName);
    }


    // unmanaged clone
    Sender::Sender(const ::qpid::messaging::Sender & s,
        Org::Apache::Qpid::Messaging::Session ^ sessRef)
        : parentSession(sessRef)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Sender (s);
        }
        catch (const ::qpid::types::Exception & error)
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

        if (newException != nullptr)
        {
            throw newException;
        }
    }


    // Destructor
    Sender::~Sender()
    {
        this->!Sender();
    }


    // Finalizer
    Sender::!Sender()
    {
        if (NULL != nativeObjPtr)
        {
            msclr::lock lk(privateLock);

            if (NULL != nativeObjPtr)
            {
                delete nativeObjPtr;
                nativeObjPtr = NULL;
            }
        }
    }


    // Copy constructor look-alike (C#)
    Sender::Sender(const Sender ^ sender)
        : parentSession(sender->parentSession)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Sender(
                *(const_cast<Sender ^>(sender)->NativeSender));
        }
        catch (const ::qpid::types::Exception & error)
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

        if (newException != nullptr)
        {
            throw newException;
        }
    }

    // Copy constructor implicitly dereferenced (C++)
    Sender::Sender(const Sender % sender)
        : parentSession(sender.parentSession)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Sender(
                *(const_cast<Sender %>(sender).NativeSender));
        }
        catch (const ::qpid::types::Exception & error)
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

        if (newException != nullptr)
        {
            throw newException;
        }
    }


    //
    // Send(msg)
    //
    void Sender::Send(Message ^ mmsgp)
    {
        Send(mmsgp, false);
    }

    void Sender::Send(Message ^ mmsgp, bool sync)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            nativeObjPtr->::qpid::messaging::Sender::send(*((*mmsgp).NativeMessage), sync);
        }
        catch (const ::qpid::types::Exception & error)
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

        if (newException != nullptr)
        {
            throw newException;
        }
    }


    void Sender::Close()
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            nativeObjPtr->close();
        }
        catch (const ::qpid::types::Exception & error)
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }

        if (newException != nullptr)
        {
            throw newException;
        }
    }

    Org::Apache::Qpid::Messaging::Address ^ Sender::GetAddress()
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception           ^ newException = nullptr;
        Messaging::Address          ^ newAddress   = nullptr;

        try
        {
            // fetch unmanaged Address
            ::qpid::messaging::Address addr =
                nativeObjPtr->getAddress();

            // create a managed Address
            newAddress = gcnew Address(addr);
        }
        catch (const ::qpid::types::Exception & error)
        {
            String ^ errmsg = gcnew String(error.what());
            newException    = gcnew QpidException(errmsg);
        }
        finally
        {
            if (newException != nullptr)
            {
                if (newAddress != nullptr)
                {
                    delete newAddress;
                }
            }
        }
        if (newException != nullptr)
        {
            throw newException;
        }

        return newAddress;
    }
}}}}
