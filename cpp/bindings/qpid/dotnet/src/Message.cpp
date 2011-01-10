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
    Message::Message()
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            messagep = new ::qpid::messaging::Message(QpidMarshal::ToNative(""));
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
            messagep = new ::qpid::messaging::Message(QpidMarshal::ToNative(theStr));
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
            messagep = new ::qpid::messaging::Message(QpidMarshal::ToNative(""));

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
		    pin_ptr<unsigned char> pBytes = &bytes[0];
		    messagep = new ::qpid::messaging::Message((char *)pBytes, bytes->Length);
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
		    pin_ptr<unsigned char> pBytes = &bytes[offset];
		    messagep = new ::qpid::messaging::Message((char *)pBytes, size);
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
            messagep = new ::qpid::messaging::Message(msgp);
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
    Message::~Message()
    {
        this->!Message();
    }


    // Finalizer
    Message::!Message()
    {
        msclr::lock lk(this);

        if (NULL != messagep)
        {
            delete messagep;
            messagep = NULL;
        }
    }

    // Copy constructor
    Message::Message(const Message ^ message)
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            messagep = new ::qpid::messaging::Message(
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

	// Property
    void Message::SetProperty(System::String ^ name, System::Object ^ value)
    {
        System::Exception ^ newException = nullptr;

        try 
		{
            ::qpid::types::Variant entryValue;
            TypeTranslator::ManagedToNativeObject(value, entryValue);

            messagep->getProperties()[QpidMarshal::ToNative(name)] = entryValue;
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
        System::Exception ^ newException = nullptr;

        try 
		{
            messagep->setContent(QpidMarshal::ToNative(content));
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
        System::Exception ^ newException = nullptr;

        try 
		{
		    pin_ptr<unsigned char> pBytes = &bytes[0];
		    messagep->setContent((char *)pBytes, bytes->Length);
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
        if ((offset + size) > bytes->Length)
			throw gcnew QpidException("Message::SetContent from byte array slice: buffer length exceeded");

        System::Exception ^ newException = nullptr;

        try 
		{
		    pin_ptr<unsigned char> pBytes = &bytes[offset];
		    messagep->setContent((char *)pBytes, size);
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
        System::String ^ result = nullptr;
        System::Exception ^ newException = nullptr;

        try 
		{
            result = gcnew String(messagep->getContent().c_str());
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
        System::Exception ^ newException = nullptr;

        try 
		{
            // Extract the message map from the message
            ::qpid::types::Variant::Map map;
            
            ::qpid::messaging::decode(*messagep, map, QpidMarshal::ToNative("amqp/map"));

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
        System::Exception ^ newException = nullptr;

        try 
		{
            // allocate a native messaging::List
            ::qpid::types::Variant::List nativeList;
            
            // Extract the list from the message in native format
            ::qpid::messaging::decode(*messagep, nativeList, QpidMarshal::ToNative("amqp/list"));

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
    // On entry message size must not be zero and
	// caller's byte array must be equal to message size.
    //
    void Message::GetContent(array<System::Byte> ^ arr)
    {
        System::Exception ^ newException = nullptr;

        try 
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
