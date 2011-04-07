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

#include "qpid/messaging/Address.h"

#include "QpidMarshal.h"
#include "QpidTypeCheck.h"
#include "TypeTranslator.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Address is a managed wrapper for a qpid::messaging::Address
    /// </summary>

    public ref class Address
    {
    private:
        // The kept object in the Messaging C++ DLL
        ::qpid::messaging::Address * addressp;

    public:
        Address();
        
        Address(System::String ^ address);

        Address(System::String ^ name,
                System::String ^ subject,
                System::Collections::Generic::Dictionary<
                    System::String ^, System::Object ^> ^ options);
                
        Address(System::String ^ name,
                System::String ^ subject,
                System::Collections::Generic::Dictionary<
                    System::String ^, System::Object ^> ^ options,
                System::String ^ type);

        // copy constructor
        Address(const Address ^ address);
        Address(const Address % address);

        // unmanaged clone
        Address(const ::qpid::messaging::Address & addrp);

        ~Address();
        !Address();

        // assignment operator
        Address % operator=(const Address % rhs)
        {
            if (this == %rhs)
            {
                // Self assignment, do nothing
            }
            else
            {
                if (NULL != addressp)
                    delete addressp;
                addressp = new ::qpid::messaging::Address(
                    *(const_cast<Address %>(rhs).NativeAddress) );
            }
            return *this;
        }

        property ::qpid::messaging::Address * NativeAddress
        {
            ::qpid::messaging::Address * get () { return addressp; }
        }

        //
        // name
        //
        property System::String ^ Name
        {
            System::String ^ get ()
            {
                return gcnew System::String(addressp->getName().c_str());
            }

            void set (System::String ^ name)
            {
                addressp->::qpid::messaging::Address::setName(QpidMarshal::ToNative(name));
            }
        }


        //
        // subject
        //
        property System::String ^ Subject
        {
            System::String ^ get ()
            {
                return gcnew System::String(addressp->getSubject().c_str());
            }

            void set (System::String ^ subject)
            {
                addressp->setSubject(QpidMarshal::ToNative(subject));
            }
        }


        //
        // options
        //
        property  System::Collections::Generic::Dictionary<
            System::String ^, System::Object ^> ^ Options
        {
            System::Collections::Generic::Dictionary<
                System::String ^, System::Object ^> ^ get ()
            {
                ::qpid::types::Variant::Map map;
                System::Collections::Generic::Dictionary<
                    System::String ^, System::Object ^> ^ newMap = 
                    gcnew System::Collections::Generic::Dictionary<
                          System::String ^, System::Object ^>;
                map = addressp->getOptions();
                TypeTranslator::NativeToManaged(map, newMap);
                return newMap;
            }


            void set (System::Collections::Generic::Dictionary<
                                System::String ^, System::Object ^> ^ options)
            {
                ::qpid::types::Variant::Map map;
                TypeTranslator::ManagedToNative(options, map);
                addressp->setOptions(map);
            }
        }


        //
        // type
        //
        property System::String ^ Type
        {
            System::String ^ get ()
            {
                return gcnew System::String(addressp->getType().c_str());
            }


            void set (System::String ^ type)
            {
                addressp->setType(QpidMarshal::ToNative(type));
            }
        }

        System::String ^ ToStr();
    };
}}}}
