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

namespace org {
namespace apache {
namespace qpid {
namespace messaging {

    /// <summary>
    /// TypeTranslator provides codec between .NET Dictionary/List and
    /// qpid messaging Map/List.
    /// </summary>

    public ref class TypeTranslator
    {

    public:
        // The given object is a Dictionary.
        // Add its elements to the qpid map.
        static void ManagedToNative(::qpid::types::Variant::Map & theMapp,
                                    QpidMap ^ theObjp);

        // The given object is a List.
        // Add its elements to the qpid list.
        static void ManagedToNative(::qpid::types::Variant::List & theListp,
                                    QpidList ^ theObjp);

        // The given object is a simple native type (not a Dictionary or List)
        // Returns a variant representing simple native type object.
        static void ManagedToNativeObject(System::Object ^ theObjp,
                                          ::qpid::types::Variant & targetp);

        // Given a Dictionary,
        // Return its values in a Qpid map
        static void NativeToManaged(QpidMap ^ dict, 
                                    ::qpid::types::Variant::Map & map);

        // Given a List,
        // Return its values in a Qpid list
        static void NativeToManaged(QpidList ^ vList, 
                                    ::qpid::types::Variant::List & qpidList);
    };
}}}}
