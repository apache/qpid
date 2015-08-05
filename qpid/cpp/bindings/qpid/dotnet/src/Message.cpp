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
#include <typeinfo.h>
#include <string>
#include <limits>
#include <iostream>
#include <stdlib.h>

#include "qpid/messaging/Message.h"
#include "qpid/types/Variant.h"

#include "QpidMarshal.h"
#include "Address.h"
#include "Duration.h"
#include "Message.h"
#include "QpidTypeCheck.h"
#include "QpidException.h"
#include "TypeTranslator.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Message is a managed wrapper for a ::qpid::messaging::Message
    /// </summary>

    // Disallow access if object has been destroyed.
    void Message::ThrowIfDisposed()
    {
        if (IsDisposed)
            throw gcnew ObjectDisposedException (GetType()->FullName);
    }


    // Create empty message
    Message::Message()
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Message(QpidMarshal::ToNative(""));
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

    // Create from string
    Message::Message(System::String ^ theStr)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Message(QpidMarshal::ToNative(theStr));
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

    // Create from object
    Message::Message(System::Object ^ theValue)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Message(QpidMarshal::ToNative(""));

            if (QpidTypeCheck::ObjectIsMap(theValue))
            {
                // Create a mapped message using given dictionary

                // Allocate a map
                ::qpid::types::Variant::Map newMap;

                // Add the map variables to the map
                TypeTranslator::ManagedToNative((QpidMap ^)theValue, newMap);

                // Set message content type
                nativeObjPtr->setContentType("ampq/map");

                // Insert the map into the message
                ::qpid::messaging::encode(newMap, *nativeObjPtr, QpidMarshal::ToNative("amqp/map"));
            }
            else if (QpidTypeCheck::ObjectIsList(theValue))
            {
                // Create a list message using given list

                // Allocate a list
                ::qpid::types::Variant::List newList;

                // Add the list variables to the list
                TypeTranslator::ManagedToNative((QpidList ^)theValue, newList);

                // Set message content type
                nativeObjPtr->setContentType("ampq/list");

                // Insert the list into the message
                ::qpid::messaging::encode(newList, *nativeObjPtr, QpidMarshal::ToNative("amqp/list"));
            }
            else
            {
                // Create a binary string message
                nativeObjPtr->setContent(QpidMarshal::ToNative(theValue->ToString()));
            }
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


    // Create from bytes
    Message::Message(array<System::Byte> ^ bytes)
    {
        System::Exception ^ newException = nullptr;
        try
        {
            privateLock = gcnew System::Object();
            pin_ptr<unsigned char> pBytes = &bytes[0];
            nativeObjPtr = new ::qpid::messaging::Message((char *)pBytes, bytes->Length);
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

    // Create from byte array slice
    Message::Message(array<System::Byte> ^ bytes, int offset, int size)
    {
        if ((offset + size) > bytes->Length)
            throw gcnew QpidException("Message::Message Create from byte array slice: buffer length exceeded");

        System::Exception ^ newException = nullptr;
        try
        {
            privateLock = gcnew System::Object();
            pin_ptr<unsigned char> pBytes = &bytes[offset];
            nativeObjPtr = new ::qpid::messaging::Message((char *)pBytes, size);
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
    Message::Message(const ::qpid::messaging::Message & msgp)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Message(msgp);
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
    // Called by .NET Dispose() or C++ delete.
    Message::~Message()
    {
        this->!Message();
    }


    // Finalizer
    // Called by Destructor or by System::GC
    Message::!Message()
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
    Message::Message(const Message ^ message)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Message(
                *(const_cast<Message ^>(message)->NativeMessage));
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
    Message::Message(const Message % message)
    {
        System::Exception ^ newException = nullptr;

        try
        {
            privateLock = gcnew System::Object();
            nativeObjPtr = new ::qpid::messaging::Message(
                *(const_cast<Message %>(message).NativeMessage));
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

    // Property
    void Message::SetProperty(System::String ^ name, System::Object ^ value)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            ::qpid::types::Variant entryValue;
            TypeTranslator::ManagedToNativeObject(value, entryValue);

            nativeObjPtr->getProperties()[QpidMarshal::ToNative(name)] = entryValue;
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

    // Content
    void Message::SetContent(System::String ^ content)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            nativeObjPtr->setContent(QpidMarshal::ToNative(content));
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


    void Message::SetContent(cli::array<System::Byte> ^ bytes)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            pin_ptr<unsigned char> pBytes = &bytes[0];
            nativeObjPtr->setContent((char *)pBytes, bytes->Length);
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


    void Message::SetContent(cli::array<System::Byte> ^ bytes, int offset, int size)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        if ((offset + size) > bytes->Length)
            throw gcnew QpidException("Message::SetContent from byte array slice: buffer length exceeded");

        System::Exception ^ newException = nullptr;

        try
        {
            pin_ptr<unsigned char> pBytes = &bytes[offset];
            nativeObjPtr->setContent((char *)pBytes, size);
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

    
    void Message::SetContentObject(System::Object ^ managedObject)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            ::qpid::types::Variant nativeObjValue;
            TypeTranslator::ManagedToNativeObject(managedObject, nativeObjValue);
            nativeObjPtr->setContentObject(nativeObjValue);
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


    System::String ^ Message::GetContent()
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::String ^ result = nullptr;
        System::Exception ^ newException = nullptr;

        try
        {
            result = QpidMarshal::ToManaged(nativeObjPtr->getContent().c_str());
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

        return result;
    }


    //
    // User wants to extract a Dictionary from the message
    //
    void Message::GetContent(System::Collections::Generic::Dictionary<
        System::String^,
        System::Object^> ^ dict)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            // Extract the message map from the message
            ::qpid::types::Variant::Map map;

            ::qpid::messaging::decode(*nativeObjPtr, map, QpidMarshal::ToNative("amqp/map"));

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
    }


    //
    // User wants to extract a list from the message
    //
    void Message::GetContent(System::Collections::ObjectModel::Collection<
        System::Object^> ^ list)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            // allocate a native messaging::List
            ::qpid::types::Variant::List nativeList;

            // Extract the list from the message in native format
            ::qpid::messaging::decode(*nativeObjPtr, nativeList, QpidMarshal::ToNative("amqp/list"));

            // translate native list into user's managed list
            TypeTranslator::NativeToManaged(nativeList, list);
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
    // Return message content to raw byte array.
    // On entry, message size must not be zero and
    // caller's byte array size must be equal to message size.
    //
    void Message::GetContent(array<System::Byte> ^ arr)
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;

        try
        {
            System::UInt32 size = (System::UInt32) nativeObjPtr->getContentSize();

            if (0 == size)
                throw gcnew QpidException("Message::GetRaw - message size is zero");

            if (arr->Length != size)
                throw gcnew QpidException("Message::GetRaw - receive buffer is wrong size");

            const char * pMsgSrc = nativeObjPtr->getContentPtr();
            pin_ptr<unsigned char> pArr = &arr[0];
            memcpy(pArr, pMsgSrc, size);
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


    System::Object ^ Message::GetContentObject()
    {
        msclr::lock lk(privateLock);
        ThrowIfDisposed();

        System::Exception ^ newException = nullptr;
        System::Object ^ result = nullptr;

        try
        {
            ::qpid::types::Variant nativeObject = nativeObjPtr->getContentObject();

            result = TypeTranslator::NativeToManagedObject(nativeObject);
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

        return result;
    }
    
    System::String ^ Message::MapAsString(System::Collections::Generic::Dictionary<
        System::String^, System::Object^> ^ dict)
    {
        System::Text::StringBuilder ^ sb = gcnew System::Text::StringBuilder("{");
        System::Exception ^ newException = nullptr;

        try
        {
            System::String ^ leading = "";

            for each (System::Collections::Generic::KeyValuePair
                <System::String^, System::Object^> kvp in dict)
            {
                sb->Append(leading);
                leading = ", ";

                if (QpidTypeCheck::ObjectIsMap(kvp.Value))
                {
                    sb->AppendFormat(
                        "{0}={1}",
                        kvp.Key,
                        MapAsString((System::Collections::Generic::Dictionary<System::String^, System::Object^> ^)kvp.Value));
                }
                else if (QpidTypeCheck::ObjectIsList(kvp.Value))
                {
                    sb->AppendFormat(
                        "{0}={1}",
                        kvp.Key,
                        ListAsString((System::Collections::ObjectModel::Collection<
                        System::Object^> ^)kvp.Value));
                }
                else if (nullptr == kvp.Value)
                {
                    sb->AppendFormat(
                        "{0}=",
                        kvp.Key);
                }
                else
                    sb->AppendFormat("{0}={1}", kvp.Key, kvp.Value);
            }
            sb->Append("}");
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

        System::String ^ result = gcnew System::String(sb->ToString());
        return result;
    }

    /// <summary>
    /// A function to display a ampq/list message packaged as a List.
    /// </summary>
    /// <param name="list">The AMQP list</param>
    System::String ^ Message::ListAsString(System::Collections::ObjectModel::Collection<System::Object^> ^ list)
    {
        System::Text::StringBuilder ^ sb = gcnew System::Text::StringBuilder("[");
        System::Exception ^ newException = nullptr;

        try
        {
            System::String ^ leading = "";

            for each (System::Object ^ obj in list)
            {
                sb->Append(leading);
                leading = ", ";

                if (QpidTypeCheck::ObjectIsMap(obj))
                {
                    sb->Append(MapAsString((System::Collections::Generic::Dictionary<
                        System::String^, System::Object^> ^)obj));
                }
                else if (QpidTypeCheck::ObjectIsList(obj))
                {
                    sb->Append(ListAsString((System::Collections::ObjectModel::Collection<
                        System::Object^> ^)obj));
                }
                else if (nullptr == obj)
                {
                    // no display for null objects
                }
                else
                    sb->Append(obj->ToString());
            }
            sb->Append("]");
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

        System::String ^ result = gcnew System::String(sb->ToString());
        return result;
    }

    System::String ^ Message::AsString(System::Object ^ obj)
    {
        if (QpidTypeCheck::ObjectIsMap(obj))
            return MapAsString((System::Collections::Generic::Dictionary<
            System::String^, System::Object^> ^)obj);
        else if (QpidTypeCheck::ObjectIsList(obj))
            return ListAsString((System::Collections::ObjectModel::Collection<
            System::Object^> ^)obj);
        else
            return obj->ToString();
    }
}}}}
