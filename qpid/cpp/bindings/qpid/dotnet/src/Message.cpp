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

#include "qpid/messaging/Message.h"
#include "qpid/types/Variant.h"

#include "QpidMarshal.h"
#include "Message.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Message is a managed wrapper for a ::qpid::messaging::Message
    /// </summary>

    // This constructor is used to create a message from bytes to put into the message
    Message::Message(System::String ^ bytes) :
        aVMap(gcnew VMap()),
        aVList(gcnew VList()),
        pVMapType(aVMap.GetType()),
        pVListType(aVList.GetType()),
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative(bytes)))
    {
    }

    // This constructor creates a message from a native received message
    Message::Message(::qpid::messaging::Message * msgp) :
        aVMap(gcnew VMap()),
        aVList(gcnew VList()),
        pVMapType(aVMap.GetType()),
        pVListType(aVList.GetType()),
        messagep(msgp)
    {
    }


    Message::Message(System::Object ^ objp) :
        aVMap(gcnew VMap()),
        aVList(gcnew VList()),
        pVMapType(aVMap.GetType()),
        pVListType(aVList.GetType()),
        messagep(new ::qpid::messaging::Message(QpidMarshal::ToNative("")))
    {
        ::qpid::types::Variant * variantp  = 0;
        std::string            * variantsp = 0;

        if (objIsMap(objp))
        {
            // Create a mapped message using given dictionary

            // Allocate a map
            ::qpid::types::Variant::Map newMap;

            // Add the map variables to the map
            Encode(newMap, (VMap ^)objp);

            // Set message content type
            messagep->setContentType("ampq/map");

            // Insert the map into the message
            ::qpid::messaging::encode(newMap, *messagep, QpidMarshal::ToNative("amqp/map"));
        }
        else if (objIsList(objp))
        {
            // Create a list message using given list

            // Allocate a list
            ::qpid::types::Variant::List newList;

            // Add the list variables to the list
            Encode(newList, (VList ^)objp);

            // Set message content type
            messagep->setContentType("ampq/list");

            // Insert the list into the message
            ::qpid::messaging::encode(newList, *messagep, QpidMarshal::ToNative("amqp/list"));
        }
        else
        {
            // Create a binary string message
            messagep->setContent(QpidMarshal::ToNative(objp->ToString()));
        }
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
    Message::Message(const Message % rhs)
    {
        messagep      = rhs.messagep;
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


    //
    // The given object is a Dictionary.
    // Add its elements to the qpid map.
    //
    void Message::Encode(::qpid::types::Variant::Map & theMapp,
                         VMap ^ theObjp)
    {
        // iterate the items, converting each to a variant and adding to the map
        for each (System::Collections::Generic::KeyValuePair<System::String^, System::Object^> kvp in theObjp)
        {
            if (objIsMap(kvp.Value))
            {
                // Recurse on inner map
                // Allocate a map
                ::qpid::types::Variant::Map newMap;

                // Add the map variables to the map
                Encode(newMap, (VMap ^)kvp.Value);

                // Create a variant entry for the inner map
                std::auto_ptr<::qpid::types::Variant> newVariantp(new ::qpid::types::Variant(newMap));

                // Get map's name
                std::string entryName = QpidMarshal::ToNative(kvp.Key);

                // Add inner map to outer map
                theMapp.insert(std::make_pair<std::string, ::qpid::types::Variant>(entryName, *newVariantp));
            }
            else if (objIsList(kvp.Value))
            {
                // Recurse on inner list
                // Allocate a list
                ::qpid::types::Variant::List newList;

                // Add the List variables to the list
                Encode(newList, (VList ^)kvp.Value);

                // Create a variant entry for the inner map
                ::qpid::types::Variant::List newVariant(newList);

                //std::auto_ptr<::qpid::types::Variant> newVariantp(new ::qpid::types::Variant(newList));

                // Get list's name
                std::string entryName = QpidMarshal::ToNative(kvp.Key);

                // Add inner list to outer map
                theMapp.insert(std::make_pair<std::string, ::qpid::types::Variant>(entryName, newVariant));
            }
            else
            {
                // Add a simple native type to map
                ::qpid::types::Variant entryValue;
                EncodeObject(kvp.Value, entryValue);
                std::string entryName = QpidMarshal::ToNative(kvp.Key);
                theMapp.insert(std::make_pair<std::string, ::qpid::types::Variant>(entryName, entryValue));
            }
        }
    }



    //
    // The given object is a List.
    // Add its elements to the qpid list.
    //
    void Message::Encode(::qpid::types::Variant::List & theListp,
                         VList ^ theObjp)
    {
        // iterate the items, converting each to a variant and adding to the map
        for each (System::Object ^ listObj in theObjp)
        {
            if (objIsMap(listObj))
            {
                // Recurse on inner map
                // Allocate a map
                ::qpid::types::Variant::Map newMap;

                // Add the map variables to the map
                Encode(newMap, (VMap ^)listObj);

                // Create a variant entry for the inner map
                std::auto_ptr<::qpid::types::Variant> newVariantp(new ::qpid::types::Variant(newMap));

                // Add inner map to outer list
                theListp.push_back(*newVariantp);
            }
            else if (objIsList(listObj))
            {
                // Recurse on inner list
                // Allocate a list
                ::qpid::types::Variant::List newList;

                // Add the List variables to the list
                Encode(newList, (VList ^)listObj);

                // Create a variant entry for the inner list
                std::auto_ptr<::qpid::types::Variant> newVariantp(new ::qpid::types::Variant(newList));

                // Add inner list to outer list
                theListp.push_back(*newVariantp);
            }
            else
            {
                // Add a simple native type to list
                ::qpid::types::Variant entryValue;
                EncodeObject(listObj, entryValue);
                theListp.push_back(entryValue);
            }
        }
    }



    //
    // Returns a variant representing simple native type object.
    // Not to be called for Map/List objects.
    //
    void Message::EncodeObject(System::Object ^ theObjp, 
                               ::qpid::types::Variant & targetp)
    {
        System::Type     ^ typeP    = (*theObjp).GetType();
        System::TypeCode   typeCode = System::Type::GetTypeCode( typeP );

        switch (typeCode)
        {
        case System::TypeCode::Boolean :
            targetp = System::Convert::ToBoolean(theObjp);
            break;

        case System::TypeCode::Byte :
            targetp = System::Convert::ToByte(theObjp);
            break;

        case System::TypeCode::UInt16 :
            targetp = System::Convert::ToUInt16(theObjp);
            break;

        case System::TypeCode::UInt32 :
            targetp = System::Convert::ToUInt32(theObjp);
            break;

        case System::TypeCode::UInt64 :
            targetp = System::Convert::ToUInt64(theObjp);
            break;

        case System::TypeCode::Char :
        case System::TypeCode::SByte :
            targetp = System::Convert::ToSByte(theObjp);
            break;

        case System::TypeCode::Int16 :
            targetp = System::Convert::ToInt16(theObjp);
            break;

        case System::TypeCode::Int32 :
            targetp = System::Convert::ToInt32(theObjp);
            break;

        case System::TypeCode::Int64 :
            targetp = System::Convert::ToInt64(theObjp);
            break;

        case System::TypeCode::Single :
            targetp = System::Convert::ToSingle(theObjp);
            break;

        case System::TypeCode::Double :
            targetp = System::Convert::ToDouble(theObjp);
            break;

        case System::TypeCode::String :
            {
                std::string      rString;
                System::String ^ rpString;

                rpString = System::Convert::ToString(theObjp);
                rString = QpidMarshal::ToNative(rpString);
                targetp = rString;
				targetp.setEncoding(QpidMarshal::ToNative("utf8"));
            }
            break;

            
        default:

            throw gcnew System::NotImplementedException();

        }
    }


    // Properties...

    //void Message::setReplyTo(System::String ^ address)
    //{
    //    messagep->setReplyTo(QpidMarshal::ToNative(address));
    //}

    //System::String ^ Message::getReplyTo()
    //{
    //    return gcnew String(messagep->getReplyTo().c_str());
    //}


    void Message::setSubject(System::String ^ subject)
    {
        messagep->setSubject(QpidMarshal::ToNative(subject));
    }
    
    System::String ^ Message::getSubject()
    {
        return gcnew String(messagep->getSubject().c_str());
    }
    

    void Message::setContentType(System::String ^ ct)
    {
        messagep->setContentType(QpidMarshal::ToNative(ct));
    }
    
    System::String ^ Message::getContentType()
    {
        return gcnew String(messagep->getContentType().c_str());
    }
    
    
    void Message::setMessageId(System::String ^ mId)
    {
        messagep->setMessageId(QpidMarshal::ToNative(mId));
    }
    
    System::String ^ Message::getMessageId()
    {
        return gcnew String(messagep->getMessageId().c_str());
    }
    
    
    void Message::setUserId(System::String ^ uId)
    {
        messagep->setUserId(QpidMarshal::ToNative(uId));
    }
    
    System::String ^ Message::getUserId()
    {
        return gcnew String(messagep->getUserId().c_str());
    }
    
    
    void Message::setCorrelationId(System::String ^ cId)
    {
        messagep->setCorrelationId(QpidMarshal::ToNative(cId));
    }
    
    System::String ^ Message::getCorrelationId()
    {
        return gcnew String(messagep->getCorrelationId().c_str());
    }
    

    void Message::setPriority(unsigned char priority)
    {
        messagep->setPriority(priority);
    }
    
    unsigned char Message::getPriority()
    {
        return messagep->getPriority();
    }
    

    //void setTtl(Duration ttl);
    //Duration getTtl();

    void Message::setDurable(bool durable)
    {
        messagep->setDurable(durable);
    }
    
    bool Message::getDurable()
    {
        return messagep->getDurable();
    }


    bool Message::getRedelivered()
    {
        return messagep->getRedelivered();
    }

    void Message::setRedelivered(bool redelivered)
    {
        messagep->setRedelivered(redelivered);
    }


    //System::String ^ Message::getProperties()
    //{
    //    pqid::types::Variant::Map * mapp = new
    //    return gcnew String(messagep->getReplyTo().c_str());
    //}


    void Message::setContent(System::String ^ content)
    {
        messagep->setContent(QpidMarshal::ToNative(content));
    }


    System::String ^ Message::getContent()
    {
        return gcnew String(messagep->getContent().c_str());
    }


    //
    // User wants to extract a Dictionary from the message
    //
    void Message::getContent(System::Collections::Generic::Dictionary<
                                System::String^, 
                                System::Object^> ^ dict)
    {
        // Extract the message map from the message
        ::qpid::types::Variant::Map map;
        
        ::qpid::messaging::decode(*messagep, map, QpidMarshal::ToNative("amqp/map"));

        Decode(dict, map);
    }


    // Given a user Dictionary and a qpid map,
    //   extract the qpid elements and put them into the dictionary.
    //
    void Message::Decode(VMap ^ dict, ::qpid::types::Variant::Map & map)
    {
        // For each object in the message map, 
        //  create a .NET object and add it to the dictionary.
        for (::qpid::types::Variant::Map::const_iterator i = map.begin(); i != map.end(); ++i) {
            // Get the name
            System::String ^ elementName = gcnew String(i->first.c_str());

            ::qpid::types::Variant     variant = i->second;
            ::qpid::types::VariantType vType   = variant.getType();

            switch (vType)
            {
            case ::qpid::types::VAR_BOOL:
                dict[elementName] = variant.asBool();
                break;
                
            case ::qpid::types::VAR_UINT8:
                dict[elementName] = variant.asUint8();
                break;
                
            case ::qpid::types::VAR_UINT16:
                dict[elementName] = variant.asUint16();
                break;
                
            case ::qpid::types::VAR_UINT32:
                dict[elementName] = variant.asUint32();
                break;
                
            case ::qpid::types::VAR_UINT64:
                dict[elementName] = variant.asUint64();
                break;
                
            case ::qpid::types::VAR_INT8:
                dict[elementName] = variant.asInt8();
                break;
                
            case ::qpid::types::VAR_INT16:
                dict[elementName] = variant.asInt16();
                break;
                
            case ::qpid::types::VAR_INT32:
                dict[elementName] = variant.asInt32();
                break;
                
            case ::qpid::types::VAR_INT64:
                dict[elementName] = variant.asInt64();
                break;
                
            case ::qpid::types::VAR_FLOAT:
                dict[elementName] = variant.asFloat();
                break;
                
            case ::qpid::types::VAR_DOUBLE:
                dict[elementName] = variant.asDouble();
                break;
                
            case ::qpid::types::VAR_STRING:
                {
                    System::String ^ elementValue = gcnew System::String(variant.asString().c_str());
                    dict[elementName] = elementValue;
                    break;
                }
            case ::qpid::types::VAR_MAP:
                {
                    VMap ^ newDict = gcnew VMap();

                    Decode (newDict, variant.asMap());

                    dict[elementName] = newDict;
                    break;
                }

            case ::qpid::types::VAR_LIST:
                {
                    VList ^ newList = gcnew VList();

                    Decode (newList, variant.asList());

                    dict[elementName] = newList;
                    break;
                }
                
            case ::qpid::types::VAR_UUID:
                break;
            }
        }
    }


    //
    // User wants to extract a list from the message
    //
    void Message::getContent(System::Collections::Generic::List<
                        System::Object^> ^ list)
    {
        // Extract the message map from the message
        ::qpid::types::Variant::List vList;
        
        ::qpid::messaging::decode(*messagep, vList, QpidMarshal::ToNative("amqp/list"));

        Decode(list, vList);
    }


    void Message::Decode(VList ^ vList, ::qpid::types::Variant::List & qpidList)
    {
        // For each object in the message map, 
        //  create a .NET object and add it to the dictionary.
        for (::qpid::types::Variant::List::const_iterator i = qpidList.begin(); i != qpidList.end(); ++i) 
        {
            ::qpid::types::Variant     variant = *i;
            ::qpid::types::VariantType vType   = variant.getType();

            switch (vType)
            {
            case ::qpid::types::VAR_BOOL:
                (*vList).Add(variant.asBool());
                break;
                
            case ::qpid::types::VAR_UINT8:
                (*vList).Add(variant.asUint8());
                break;
                
            case ::qpid::types::VAR_UINT16:
                (*vList).Add(variant.asUint16());
                break;
                
            case ::qpid::types::VAR_UINT32:
                (*vList).Add(variant.asUint32());
                break;
                
            case ::qpid::types::VAR_UINT64:
                (*vList).Add(variant.asUint64());
                break;
                
            case ::qpid::types::VAR_INT8:
                (*vList).Add(variant.asInt8());
                break;
                
            case ::qpid::types::VAR_INT16:
                (*vList).Add(variant.asInt16());
                break;
                
            case ::qpid::types::VAR_INT32:
                (*vList).Add(variant.asInt32());
                break;
                
            case ::qpid::types::VAR_INT64:
                (*vList).Add(variant.asInt64());
                break;
                
            case ::qpid::types::VAR_FLOAT:
                (*vList).Add(variant.asFloat());
                break;
                
            case ::qpid::types::VAR_DOUBLE:
                (*vList).Add(variant.asDouble());
                break;
                
            case ::qpid::types::VAR_STRING:
                {
                    System::String ^ elementValue = gcnew System::String(variant.asString().c_str());
                    (*vList).Add(elementValue);
                    break;
                }
            case ::qpid::types::VAR_MAP:
                {
                    VMap ^ newDict = gcnew VMap();

                    Decode (newDict, variant.asMap());

                    (*vList).Add(newDict);
                    break;
                }

            case ::qpid::types::VAR_LIST:
                {
                    VList ^ newList = gcnew VList();

                    Decode (newList, variant.asList());

                    (*vList).Add(newList);
                    break;
                }
                
            case ::qpid::types::VAR_UUID:
                break;
            }
        }
    }
}}}}
