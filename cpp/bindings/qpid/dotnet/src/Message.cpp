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

    // Create empty message
    Message::Message() :
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative("")))
    {
    }

    // Create from string
    Message::Message(System::String ^ theStr) :
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative(theStr)))
    {
    }

    // Create from object
    Message::Message(System::Object ^ theValue) :
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative("")))
    {
        if (QpidTypeCheck::ObjectIsMap(theValue))
        {
            // Create a mapped message using given dictionary

            // Allocate a map
            ::qpid::types::Variant::Map newMap;

            // Add the map variables to the map
            TypeTranslator::ManagedToNative((QpidMap ^)theValue, newMap);

            // Set message content type
            messagep->setContentType("ampq/map");

            // Insert the map into the message
            ::qpid::messaging::encode(newMap, *messagep, QpidMarshal::ToNative("amqp/map"));
        }
        else if (QpidTypeCheck::ObjectIsList(theValue))
        {
            // Create a list message using given list

            // Allocate a list
            ::qpid::types::Variant::List newList;

            // Add the list variables to the list
            TypeTranslator::ManagedToNative((QpidList ^)theValue, newList);

            // Set message content type
            messagep->setContentType("ampq/list");

            // Insert the list into the message
            ::qpid::messaging::encode(newList, *messagep, QpidMarshal::ToNative("amqp/list"));
        }
        else
        {
            // Create a binary string message
            messagep->setContent(QpidMarshal::ToNative(theValue->ToString()));
        }
    }


	// Create from bytes
	Message::Message(array<System::Byte> ^ bytes)
	{
		pin_ptr<unsigned char> pBytes = &bytes[0];
		messagep = new ::qpid::messaging::Message((char *)pBytes, bytes->Length);
	}

    // Create from byte array slice
	Message::Message(array<System::Byte> ^ bytes, int offset, int size)
	{
        if ((offset + size) > bytes->Length)
			throw gcnew QpidException("Message::Message Create from byte array slice: buffer length exceeded");

		pin_ptr<unsigned char> pBytes = &bytes[offset];
		messagep = new ::qpid::messaging::Message((char *)pBytes, size);
	}


	// unmanaged clone
    Message::Message(const ::qpid::messaging::Message & msgp) :
        messagep(new ::qpid::messaging::Message(msgp))
    {
    }


    // Destructor
    Message::~Message()
    {
        Cleanup();
    }


    // Finalizer
    Message::!Message()
    {
        Cleanup();
    }

    // Copy constructor
    Message::Message(const Message ^ message)
        : messagep(new ::qpid::messaging::Message(
                        *(const_cast<Message ^>(message)->NativeMessage)))
    {
    }

    // Destroys kept object
    // TODO: add lock
    void Message::Cleanup()
    {
        if (NULL != messagep)
        {
            delete messagep;
            messagep = NULL;
        }
    }

	// Property
    void Message::SetProperty(System::String ^ name, System::Object ^ value)
    {
        ::qpid::types::Variant entryValue;
        TypeTranslator::ManagedToNativeObject(value, entryValue);

        messagep->getProperties()[QpidMarshal::ToNative(name)] = entryValue;
    }

	// Content
	void Message::SetContent(System::String ^ content)
    {
        messagep->setContent(QpidMarshal::ToNative(content));
    }


    void Message::SetContent(cli::array<System::Byte> ^ bytes)
    {
		pin_ptr<unsigned char> pBytes = &bytes[0];
		messagep->setContent((char *)pBytes, bytes->Length);
    }


    void Message::SetContent(cli::array<System::Byte> ^ bytes, int offset, int size)
    {
        if ((offset + size) > bytes->Length)
			throw gcnew QpidException("Message::SetContent from byte array slice: buffer length exceeded");

		pin_ptr<unsigned char> pBytes = &bytes[offset];
		messagep->setContent((char *)pBytes, size);
    }


    System::String ^ Message::GetContent()
    {
        return gcnew String(messagep->getContent().c_str());
    }


    //
    // User wants to extract a Dictionary from the message
    //
    void Message::GetContent(System::Collections::Generic::Dictionary<
                                System::String^, 
                                System::Object^> ^ dict)
    {
        // Extract the message map from the message
        ::qpid::types::Variant::Map map;
        
        ::qpid::messaging::decode(*messagep, map, QpidMarshal::ToNative("amqp/map"));

        TypeTranslator::NativeToManaged(map, dict);
    }


    //
    // User wants to extract a list from the message
    //
    void Message::GetContent(System::Collections::ObjectModel::Collection<
                        System::Object^> ^ list)
    {
        // allocate a native messaging::List
        ::qpid::types::Variant::List nativeList;
        
        // Extract the list from the message in native format
        ::qpid::messaging::decode(*messagep, nativeList, QpidMarshal::ToNative("amqp/list"));

        // translate native list into user's managed list
        TypeTranslator::NativeToManaged(nativeList, list);
    }

    //
    // Return message content to raw byte array.
    // On entry message size must not be zero and
	// caller's byte array must be equal to message size.
    //
    void Message::GetContent(array<System::Byte> ^ arr)
    {
        System::UInt32 size = messagep->getContentSize();
     
        if (0 == size)
            throw gcnew QpidException("Message::GetRaw - message size is zero");

        if (arr->Length != size)
            throw gcnew QpidException("Message::GetRaw - receive buffer is wrong size");

        const char * pMsgSrc = messagep->getContentPtr();
		pin_ptr<unsigned char> pArr = &arr[0];
		memcpy(pArr, pMsgSrc, size);
    }


	System::String ^ Message::MapAsString(System::Collections::Generic::Dictionary<
					           System::String^, System::Object^> ^ dict)
    {
		System::String ^ leading = "";
		System::Text::StringBuilder ^ sb = gcnew System::Text::StringBuilder("{");

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

		System::String ^ result = gcnew System::String(sb->ToString());
		return result;
    }

    /// <summary>
    /// A function to display a ampq/list message packaged as a List.
    /// </summary>
    /// <param name="list">The AMQP list</param>
	System::String ^ Message::ListAsString(System::Collections::ObjectModel::Collection<System::Object^> ^ list)
    {
		System::String ^ leading = "";
		System::Text::StringBuilder ^ sb = gcnew System::Text::StringBuilder("[");

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
