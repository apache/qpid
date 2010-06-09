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
        // Kept object deletion code
        void Cleanup();

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

        // Create from received address
        Address(::qpid::messaging::Address * addrp);

        ~Address();
        !Address();
//        Address(const Address % rhs);

        property ::qpid::messaging::Address * NativeAddress
        {
            ::qpid::messaging::Address * get () { return addressp; }
        }

        System::String ^ GetName();
        void SetName(System::String ^ name);

        System::String ^ GetSubject();
        void SetSubject(System::String ^ subject);

        System::Collections::Generic::Dictionary<
            System::String ^, System::Object ^> ^ GetOptions();

        void SetOptions(System::Collections::Generic::Dictionary<
                            System::String ^, System::Object ^> ^ options);

        System::String ^ GetType();
        void SetType(System::String ^ type);

        System::String ^ ToStr();
    };
}}}}
