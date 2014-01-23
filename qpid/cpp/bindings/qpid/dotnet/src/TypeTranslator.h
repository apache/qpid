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

#include "qpid/types/Variant.h"

#include "QpidTypeCheck.h"

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// TypeTranslator provides codec between .NET Dictionary/List and
    /// qpid messaging Map/List.
    /// </summary>
    public ref class TypeTranslator sealed
    {
    private:
        TypeTranslator::TypeTranslator() {}

    public:
        // The given object is a managed Dictionary.
        // Add its elements to the qpid map.
        static void ManagedToNative(
            QpidMap ^ theDictionary,
            ::qpid::types::Variant::Map & qpidMap);

        // The given object is a managed List.
        // Add its elements to the qpid list.
        static void ManagedToNative(
            QpidList ^ theList,
            ::qpid::types::Variant::List & qpidList);

        // The given object is a simple managed type (not a Dictionary or List)
        // Returns a variant representing simple native type object.
        static void ManagedToNativeObject(
            System::Object ^ managedValue,
            ::qpid::types::Variant & qpidVariant);

        // The given object is a qpid map.
        // Add its elements to the managed Dictionary.
        static void NativeToManaged(
            ::qpid::types::Variant::Map & qpidMap,
            QpidMap ^ dict);

        // The given object is a qpid list.
        // Add its elements to the managed List.
        static void NativeToManaged(
            ::qpid::types::Variant::List & qpidList,
            QpidList ^ managedList);

        // The given object is a qpid Variant
        // Return it as a managed System::Object
        static System::Object ^ NativeToManagedObject(
            ::qpid::types::Variant & nativeObject);
    };
}}}}
