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

#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/exceptions.h"

#include "Receiver.h"
#include "Address.h"
#include "Session.h"
#include "Message.h"
#include "Duration.h"
#include "QpidException.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Receiver is a managed wrapper for a ::qpid::messaging::Receiver
    /// </summary>

    // Disallow access if object has been destroyed.
    void Receiver::ThrowIfDisposed()
    {
        if (IsDisposed)
            throw gcnew ObjectDisposedException (GetType()->FullName);
    }


    // unmanaged clone
    Receiver::Receiver(const ::qpid::messaging::Receiver & r,
        Org::Apache::Qpid::Messaging::Session ^ sessRef)
        : parentSession(sessRef)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Receiver (r);
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

    // unmanaged clone
    // undefined

    // Destructor
    Receiver::~Receiver()
    {
        this->!Receiver();
    }


    // Finalizer
    Receiver::!Receiver()
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
    Receiver::Receiver(const Receiver ^ receiver)
        : parentSession(receiver->parentSession)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Receiver(
                *(const_cast<Receiver ^>(receiver)->NativeReceiver));
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
    Receiver::Receiver(const Receiver % receiver)
        : parentSession(receiver.parentSession)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Receiver(
                *(const_cast<Receiver %>(receiver).NativeReceiver));
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
    // Get(message)
    //
    bool Receiver::Get(Message ^% mmsgp)
    {
        return Get(mmsgp, DurationConstants::FORVER);
    }

    bool Receiver::Get(Message ^% mmsgp, Duration ^ durationp)
    {
        System::Exception           ^ newException = nullptr;

        try
        {
            msclr::lock lk(privateLock);
            ThrowIfDisposed();

            ::qpid::messaging::Duration dur((*durationp).Milliseconds);

            ::qpid::messaging::Message tmpMsg;

            bool result = nativeObjPtr->Receiver::get(tmpMsg, dur);

            if (result)
            {
                mmsgp = gcnew Message(tmpMsg);
            }

            return result;
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

        return false;
    }

    //
    // message = Get()
    //
    Message ^ Receiver::Get()
    {
        return Get(DurationConstants::FORVER);
    }


    Message ^ Receiver::Get(Duration ^ durationp)
    {
        System::Exception          ^ newException = nullptr;
        Message                    ^ newMessage   = nullptr;

        try
        {
            msclr::lock lk(privateLock);
            ThrowIfDisposed();

            // translate the duration
            ::qpid::messaging::Duration dur((*durationp).Milliseconds);

            // get the message
            ::qpid::messaging::Message msg =
                nativeObjPtr->::qpid::messaging::Receiver::get(dur);

            // create new managed message with received message embedded in it
            newMessage = gcnew Message(msg);
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
                if (newMessage != nullptr)
                {
                    delete newMessage;
                }
            }
        }
        if (newException != nullptr)
        {
            throw newException;
        }

        return newMessage;
    }

    //
    // Fetch(message)
    //
    bool Receiver::Fetch(Message ^% mmsgp)
    {
        return Fetch(mmsgp, DurationConstants::FORVER);
    }

    bool Receiver::Fetch(Message ^% mmsgp, Duration ^ durationp)
    {
        System::Exception         ^ newException = nullptr;

        try
        {
            msclr::lock lk(privateLock);
            ThrowIfDisposed();

            ::qpid::messaging::Duration dur((*durationp).Milliseconds);

            ::qpid::messaging::Message tmpMsg;

            bool result = nativeObjPtr->Receiver::fetch(tmpMsg, dur);

            if (result)
            {
                mmsgp = gcnew Message(tmpMsg);
            }

            return result;
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

        return false;
    }


    //
    // message = Fetch()
    //

    Message ^ Receiver::Fetch()
    {
        return Fetch(DurationConstants::FORVER);
    }

    Message ^ Receiver::Fetch(Duration ^ durationp)
    {
        System::Exception         ^ newException = nullptr;
        Message                   ^ newMessage   = nullptr;

        try
        {
            msclr::lock lk(privateLock);
            ThrowIfDisposed();

            // translate the duration
            ::qpid::messaging::Duration dur((*durationp).Milliseconds);

            // get the message
            ::qpid::messaging::Message msg =
                nativeObjPtr->::qpid::messaging::Receiver::fetch(dur);

            // create new managed message with received message embedded in it
            newMessage = gcnew Message(msg);
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
                if (newMessage != nullptr)
                {
                    delete newMessage;
                }
            }
        }
        if (newException != nullptr)
        {
            throw newException;
        }

        return newMessage;
    }

    void Receiver::Close()
    {
        System::Exception         ^ newException = nullptr;
        Message                   ^ newMessage   = nullptr;

        try
        {
            msclr::lock lk(privateLock);
            ThrowIfDisposed();

            nativeObjPtr->close();
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
                if (newMessage != nullptr)
                {
                    delete newMessage;
                }
            }
        }
        if (newException != nullptr)
        {
            throw newException;
        }
    }

    Org::Apache::Qpid::Messaging::Address ^ Receiver::GetAddress()
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
