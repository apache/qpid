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

#include "Address.h"
#include "QpidMarshal.h"
#include "QpidTypeCheck.h"
#include "TypeTranslator.h"

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// Address is a managed wrapper for a qpid::messaging::Address
    /// </summary>

    // Create empty
    Address::Address() :
        addressp(new ::qpid::messaging::Address(QpidMarshal::ToNative("")))
    {
    }

    // Create string address
    Address::Address(System::String ^ address) :
        addressp(new ::qpid::messaging::Address(QpidMarshal::ToNative(address)))
    {
    }

    // Create with options
    Address::Address(System::String ^ name, 
                     System::String ^ subject,
                     System::Collections::Generic::Dictionary<
                         System::String ^, System::Object ^> ^ options) :
        addressp(new ::qpid::messaging::Address())
    {
        setName(name);
        setSubject(subject);
        setOptions(options);
        setType("");
    }


    Address::Address(System::String ^ name, 
                     System::String ^ subject,
                     System::Collections::Generic::Dictionary<
                         System::String ^, System::Object ^> ^ options,
                     System::String ^ type) :
        addressp(new ::qpid::messaging::Address())
    {
        setName(name);
        setSubject(subject);
        setOptions(options);
        setType(type);
    }


    // Create from received address
    Address::Address(::qpid::messaging::Address * addrp) :
        addressp(addrp)
    {
    }

    // Destructor
    Address::~Address()
    {
        Cleanup();
    }


    // Finalizer
    Address::!Address()
    {
        Cleanup();
    }


    // Destroys kept object
    // TODO: add lock
    void Address::Cleanup()
    {
        if (NULL != addressp)
        {
            delete addressp;
            addressp = NULL;
        }
    }


    //
    // name
    //
    System::String ^ Address::getName()
    {
        return gcnew System::String(addressp->getName().c_str());
    }

    void Address::setName(System::String ^ name)
    {
        addressp->::qpid::messaging::Address::setName(QpidMarshal::ToNative(name));
    }

    //
    // subject
    //
    System::String ^ Address::getSubject()
    {
        return gcnew System::String(addressp->getSubject().c_str());
    }

    void Address::setSubject(System::String ^ subject)
    {
        addressp->setName(QpidMarshal::ToNative(subject));
    }

    //
    // options
    //
    System::Collections::Generic::Dictionary<
        System::String ^, System::Object ^> ^ Address::getOptions()
    {
        ::qpid::types::Variant::Map map;
        System::Collections::Generic::Dictionary<
            System::String ^, System::Object ^> ^ newMap = 
            gcnew System::Collections::Generic::Dictionary<
                  System::String ^, System::Object ^>;
        map = addressp->getOptions();
        TypeTranslator::NativeToManaged(newMap, map);
        return newMap;
    }


    void Address::setOptions(System::Collections::Generic::Dictionary<
                        System::String ^, System::Object ^> ^ options)
    {
        ::qpid::types::Variant::Map map;
        TypeTranslator::ManagedToNative(map, options);
        addressp->setOptions(map);
    }

    //
    // type
    //
    System::String ^ Address::getType()
    {
        return gcnew System::String(addressp->getType().c_str());
    }


    void Address::setType(System::String ^ type)
    {
        addressp->setName(QpidMarshal::ToNative(type));
    }

    //
    // str
    //
    System::String ^ Address::str()
    {
        return gcnew System::String(addressp->str().c_str());
    }
}}}}
