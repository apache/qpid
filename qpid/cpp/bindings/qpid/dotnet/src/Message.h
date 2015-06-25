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

#include "qpid/messaging/Message.h"

#include "QpidMarshal.h"
#include "Address.h"
#include "Duration.h"
#include "QpidException.h"
#include "TypeTranslator.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    ref class Address;
    ref class Duration;

    /// <summary>
    /// Message is a managed wrapper for a ::qpid::messaging::Message
    /// </summary>

    public ref class Message
    {

    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Message * nativeObjPtr;

        // per-instance lock object
        System::Object ^ privateLock;

        // Disallow use after object is destroyed
        void ThrowIfDisposed();

    public:
        // Create empty message
        Message();

        // Create from String
        Message(System::String ^ theStr);

        // Create from object
        Message(System::Object ^ theValue);

        // Create from byte array
        Message(array<System::Byte> ^ bytes);

        // Create from byte array slice
        Message(array<System::Byte> ^ bytes, int offset, int size);

        // System destructor/finalizer entry points
        ~Message();
        !Message();

        // Copy constructor
        Message(const Message ^ message);
        Message(const Message % message);

        // unmanaged clone
        Message(const ::qpid::messaging::Message & msgp);

        // assignment operator
        Message % operator=(const Message % rhs)
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
                nativeObjPtr = new ::qpid::messaging::Message(
                    *(const_cast<Message %>(rhs).NativeMessage) );
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
        // NativeMessage
        //
        property ::qpid::messaging::Message * NativeMessage
        {
            ::qpid::messaging::Message * get ()
            {
                return nativeObjPtr;
            }
        }

        //
        // ReplyTo
        //
        property Address ^ ReplyTo
        {
            void set (Address ^ address)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setReplyTo(*(address->NativeAddress));
            }

            Address ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                const ::qpid::messaging::Address & addrp =
                    nativeObjPtr->::qpid::messaging::Message::getReplyTo();

                return gcnew Address(addrp);
            }
        }

        //
        // Subject
        //
        property System::String ^ Subject
        {
            void set (System::String ^ subject)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setSubject(QpidMarshal::ToNative(subject));
            }

            System::String ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return gcnew String(nativeObjPtr->getSubject().c_str());
            }
        }


        //
        // ContentType
        //
        property System::String ^ ContentType
        {
            void set (System::String ^ ct)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setContentType(QpidMarshal::ToNative(ct));
            }

            System::String ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return gcnew String(nativeObjPtr->::qpid::messaging::Message::getContentType().c_str());
            }
        }


        //
        // MessageId
        //
        property System::String ^ MessageId
        {
            void set (System::String ^ messageId)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setMessageId(QpidMarshal::ToNative(messageId));
            }

            System::String ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return gcnew String(nativeObjPtr->getMessageId().c_str());
            }
        }


        //
        // UserId
        //
        property System::String ^ UserId
        {
            void set (System::String ^ uId)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setUserId(QpidMarshal::ToNative(uId));
            }

            System::String ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return gcnew String(nativeObjPtr->getUserId().c_str());
            }
        }


        //
        // CorrelationId
        //
        property System::String ^ CorrelationId
        {
            void set (System::String ^ correlationId)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setCorrelationId(QpidMarshal::ToNative(correlationId));
            }

            System::String ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return gcnew String(nativeObjPtr->getCorrelationId().c_str());
            }
        }


        //
        // Priority
        //
        property unsigned char Priority
        {
            void set (unsigned char priority)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setPriority(priority);
            }

            unsigned char get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getPriority();
            }
        }


        //
        // Ttl
        //
        property Duration ^ Ttl
        {
            void set (Duration ^ ttl)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                ::qpid::messaging::Duration dur(ttl->Milliseconds);

                nativeObjPtr->setTtl(dur);
            }

            Duration ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                Duration ^ dur = gcnew Duration(nativeObjPtr->getTtl().getMilliseconds());

                return dur;
            }
        }

        //
        // Durable
        //
        property bool Durable
        {
            void set (bool durable)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setDurable(durable);
            }

            bool get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getDurable();
            }
        }

        //
        // Redelivered
        //
        property bool Redelivered
        {
            bool get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getRedelivered();
            }

            void set (bool redelivered)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                nativeObjPtr->setRedelivered(redelivered);
            }
        }

        //
        // Property
        //
        void Message::SetProperty(System::String ^ name, System::Object ^ value);

        //
        // Properties
        //
        property System::Collections::Generic::Dictionary<
            System::String^, System::Object^> ^ Properties
        {
            System::Collections::Generic::Dictionary<
                System::String^, System::Object^> ^ get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                System::Exception ^ newException = nullptr;

                System::Collections::Generic::Dictionary<System::String^, System::Object^> ^ dict =
                    gcnew System::Collections::Generic::Dictionary<System::String^, System::Object^> ;

                try
                {
                    ::qpid::types::Variant::Map map;
                    map = nativeObjPtr->getProperties();
                    TypeTranslator::NativeToManaged(map, dict);
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

                return dict;
            }


            void set (System::Collections::Generic::Dictionary<
                System::String^, System::Object^> ^ properties)
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                System::Exception ^ newException = nullptr;

                try
                {
                    ::qpid::types::Variant::Map variantMap;
                    TypeTranslator::ManagedToNative(properties, variantMap);
                    nativeObjPtr->setProperties(variantMap);
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
        }


        void SetContent(System::String ^ content);

        void SetContent(cli::array<System::Byte> ^ bytes);

        void SetContent(cli::array<System::Byte> ^ bytes, int offset, int size);

        void SetContentObject(System::Object ^ managedObject);

        // get content as string
        System::String ^ GetContent();

        // get content as dictionary
        void GetContent(System::Collections::Generic::Dictionary<
            System::String^,
            System::Object^> ^ dict);

        // get content as map
        void GetContent(System::Collections::ObjectModel::Collection<
            System::Object^> ^);

        // get content as bytes
        void GetContent(cli::array<System::Byte> ^ arr);

        // get content as object
        System::Object ^ GetContentObject();

        //
        // ContentSize
        //
        property System::UInt64 ContentSize
        {
            System::UInt64 get ()
            {
                msclr::lock lk(privateLock);
                ThrowIfDisposed();

                return nativeObjPtr->getContentSize();
            }
        }


        // A message has been returned to managed code through GetContent().
        // Display the content of that System::Object as a string.
        System::String ^ AsString(System::Object ^ obj);

        System::String ^ MapAsString(System::Collections::Generic::Dictionary<
            System::String^, System::Object^> ^ dict);

        System::String ^ ListAsString(System::Collections::ObjectModel::Collection<
            System::Object^> ^ list);

        //TODO: EncodingException

        // Note: encode/decode functions are in TypeTranslator
    };
}}}}
