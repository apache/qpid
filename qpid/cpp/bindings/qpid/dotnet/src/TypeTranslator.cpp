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

#include "TypeTranslator.h"
#include "QpidTypeCheck.h"
#include "QpidMarshal.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Translate between managed and native types.
    /// </summary>

    //
    // The given object is a Dictionary.
    // Add its elements to the qpid map.
    //
    void TypeTranslator::ManagedToNative(
        QpidMap ^ theDictionary,
        ::qpid::types::Variant::Map & qpidMap)
    {
        // iterate the items, converting each to a variant and adding to the map
        for each (System::Collections::Generic::KeyValuePair
            <System::String^, System::Object^> kvp in theDictionary)
        {
            if (QpidTypeCheck::ObjectIsMap(kvp.Value))
            {
                // Recurse on inner map
                // Allocate a map
                ::qpid::types::Variant::Map newMap;

                // Add the map variables to the map
                ManagedToNative((QpidMap ^)kvp.Value, newMap);

                // Create a variant entry for the inner map
                std::auto_ptr<::qpid::types::Variant> newVariantp(new ::qpid::types::Variant(newMap));

                // Get map's name
                std::string entryName = QpidMarshal::ToNative(kvp.Key);

                // Add inner map to outer map
                qpidMap.insert(std::make_pair(entryName, *newVariantp));
            }
            else if (QpidTypeCheck::ObjectIsList(kvp.Value))
            {
                // Recurse on inner list
                // Allocate a list
                ::qpid::types::Variant::List newList;

                // Add the List variables to the list
                ManagedToNative((QpidList ^)kvp.Value, newList);

                // Create a variant entry for the inner map
                ::qpid::types::Variant::List newVariant(newList);

                //std::auto_ptr<::qpid::types::Variant> newVariantp(new ::qpid::types::Variant(newList));

                // Get list's name
                std::string entryName = QpidMarshal::ToNative(kvp.Key);

                // Add inner list to outer map
                qpidMap.insert(std::make_pair(entryName, newVariant));
            }
            else
            {
                // Add a simple native type to map
                ::qpid::types::Variant entryValue;
                if (nullptr != kvp.Value)
                {
                    ManagedToNativeObject(kvp.Value, entryValue);
                }
                std::string entryName = QpidMarshal::ToNative(kvp.Key);
                qpidMap.insert(std::make_pair(entryName, entryValue));
            }
        }
    }



    //
    // The given object is a List.
    // Add its elements to the qpid list.
    //
    void TypeTranslator::ManagedToNative(
        QpidList ^ theList,
        ::qpid::types::Variant::List & qpidList)
    {
        // iterate the items, converting each to a variant and adding to the map
        for each (System::Object ^ listObj in theList)
        {
            if (QpidTypeCheck::ObjectIsMap(listObj))
            {
                // Recurse on inner map
                // Allocate a map
                ::qpid::types::Variant::Map newMap;

                // Add the map variables to the map
                ManagedToNative((QpidMap ^)listObj, newMap);

                // Create a variant entry for the inner map
                std::auto_ptr<::qpid::types::Variant> newVariantp(new ::qpid::types::Variant(newMap));

                // Add inner map to outer list
                qpidList.push_back(*newVariantp);
            }
            else if (QpidTypeCheck::ObjectIsList(listObj))
            {
                // Recurse on inner list
                // Allocate a list
                ::qpid::types::Variant::List newList;

                // Add the List variables to the list
                ManagedToNative((QpidList ^)listObj, newList);

                // Create a variant entry for the inner list
                std::auto_ptr<::qpid::types::Variant> newVariantp(new ::qpid::types::Variant(newList));

                // Add inner list to outer list
                qpidList.push_back(*newVariantp);
            }
            else
            {
                // Add a simple native type to list
                ::qpid::types::Variant entryValue;
                if (nullptr != listObj)
                {
                    ManagedToNativeObject(listObj, entryValue);
                }
                qpidList.push_back(entryValue);
            }
        }
    }



    //
    // Returns a variant representing simple native type object.
    // Not to be called for Map/List objects.
    //
    void TypeTranslator::ManagedToNativeObject(
        System::Object ^ managedValue,
        ::qpid::types::Variant & qpidVariant)
    {
        System::Type     ^ typeP    = (*managedValue).GetType();
        System::TypeCode   typeCode = System::Type::GetTypeCode( typeP );

        switch (typeCode)
        {
        case System::TypeCode::Boolean :
            qpidVariant = System::Convert::ToBoolean(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::Byte :
            qpidVariant = System::Convert::ToByte(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::UInt16 :
            qpidVariant = System::Convert::ToUInt16(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::UInt32 :
            qpidVariant = System::Convert::ToUInt32(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::UInt64 :
            qpidVariant = System::Convert::ToUInt64(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::Char :
        case System::TypeCode::SByte :
            qpidVariant = System::Convert::ToSByte(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::Int16 :
            qpidVariant = System::Convert::ToInt16(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::Int32 :
            qpidVariant = System::Convert::ToInt32(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::Int64 :
            qpidVariant = System::Convert::ToInt64(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::Single :
            qpidVariant = System::Convert::ToSingle(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::Double :
            qpidVariant = System::Convert::ToDouble(managedValue, System::Globalization::CultureInfo::InvariantCulture);
            break;

        case System::TypeCode::String :
            {
                std::string      rString;
                System::String ^ rpString;

                rpString = System::Convert::ToString(managedValue, System::Globalization::CultureInfo::InvariantCulture);
                rString = QpidMarshal::ToNative(rpString);
                qpidVariant = rString;
                qpidVariant.setEncoding(QpidMarshal::ToNative("utf8"));
            }
            break;

        case System::TypeCode::Object :
            {
                //
                // Derived classes
                //
                if ("System.Guid" == typeP->ToString())
                {
                    cli::array<System::Byte> ^ guidBytes = ((System::Guid)managedValue).ToByteArray();
                    pin_ptr<unsigned char> pinnedBuf = &guidBytes[0];
                    ::qpid::types::Uuid newUuid = ::qpid::types::Uuid(pinnedBuf);
                    qpidVariant = newUuid;
                }
                else if (QpidTypeCheck::ObjectIsMap(managedValue))
                {
                    ::qpid::types::Variant::Map newMap;
                    ManagedToNative((QpidMap ^)managedValue, newMap);
                    qpidVariant = newMap;
                }
                else if (QpidTypeCheck::ObjectIsList(managedValue))
                {
                    ::qpid::types::Variant::List newList;
                    ManagedToNative((QpidList ^)managedValue, newList);
                    qpidVariant = newList;
                }
                else
                {
                    throw gcnew System::NotImplementedException();
                }
            }
            break;

        default:

            throw gcnew System::NotImplementedException();

        }
    }


    // Given a user Dictionary and a qpid map,
    //   extract the qpid elements and put them into the dictionary.
    //
    void TypeTranslator::NativeToManaged(
        ::qpid::types::Variant::Map & qpidMap,
        QpidMap ^ dict)
    {
        // For each object in the message map,
        //  create a .NET object and add it to the dictionary.
        for (::qpid::types::Variant::Map::const_iterator i = qpidMap.begin(); i != qpidMap.end(); ++i)
        {
            System::String ^ elementName = QpidMarshal::ToManaged(i->first.c_str());
            ::qpid::types::Variant variant = i->second;
            dict[elementName] = NativeToManagedObject(variant);
        }
    }


    void TypeTranslator::NativeToManaged(
        ::qpid::types::Variant::List & qpidList,
        QpidList ^ managedList)
    {
        // For each object in the qpidList
        //  create a .NET object and add it to the managed List.
        for (::qpid::types::Variant::List::const_iterator i = qpidList.begin(); i != qpidList.end(); ++i)
        {
            ::qpid::types::Variant     variant = *i;
            (*managedList).Add( NativeToManagedObject(variant) );
        }
    }


    System::Object ^ TypeTranslator::NativeToManagedObject(
        ::qpid::types::Variant & nativeObject)
    {
        //  create a .NET object and return it
        ::qpid::types::VariantType vType   = nativeObject.getType();
        System::Object ^ managedObject = nullptr;

        switch (vType)
        {
        case ::qpid::types::VAR_VOID:
            {
                break;
            }

        case ::qpid::types::VAR_BOOL:
            {
                bool result = nativeObject.asBool();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_UINT8:
            {
                byte result = nativeObject.asUint8();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_UINT16:
            {
                unsigned short result = nativeObject.asUint16();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_UINT32:
            {
                unsigned long result = nativeObject.asUint32();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_UINT64:
            {
                unsigned __int64 result = nativeObject.asUint64();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_INT8:
            {
                System::SByte result = nativeObject.asInt8();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_INT16:
            {
                short result = nativeObject.asInt16();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_INT32:
            {
                long result = nativeObject.asInt32();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_INT64:
            {
                __int64 result = nativeObject.asInt64();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_FLOAT:
            {
                float result = nativeObject.asFloat();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_DOUBLE:
            {
                double result = nativeObject.asDouble();
                managedObject = result;
                break;
            }

        case ::qpid::types::VAR_STRING:
            {
                System::String ^ elementValue = QpidMarshal::ToManaged(nativeObject.asString().c_str());
                managedObject = elementValue;
                break;
            }
        case ::qpid::types::VAR_MAP:
            {
                QpidMap ^ newDict = gcnew QpidMap();

                NativeToManaged(nativeObject.asMap(), newDict);

                managedObject = newDict;
                break;
            }

        case ::qpid::types::VAR_LIST:
            {
                QpidList ^ newList = gcnew QpidList();

                NativeToManaged(nativeObject.asList(), newList);

                managedObject = newList;
                break;
            }

        case ::qpid::types::VAR_UUID:
            {
                System::String ^ elementValue = gcnew System::String(nativeObject.asUuid().str().c_str());
                System::Guid ^ newGuid = System::Guid(elementValue);
                managedObject = newGuid;
            }
            break;
        }

        return managedObject;
    }
}}}}
