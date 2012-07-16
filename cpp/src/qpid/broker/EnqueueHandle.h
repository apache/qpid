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

/**
 * \file EnqueueHandle.h
 */

#ifndef qpid_broker_EnqueueHandle_h_
#define qpid_broker_EnqueueHandle_h_

#include "Handle.h"

#include "qpid/asyncStore/AsyncStoreHandle.h"

namespace qpid {
namespace asyncStore {
class EnqueueHandleImpl;
}
namespace broker {

class EnqueueHandle : public Handle<qpid::asyncStore::EnqueueHandleImpl>,
                      public qpid::asyncStore::AsyncStoreHandle
{
public:
    EnqueueHandle(qpid::asyncStore::EnqueueHandleImpl* p = 0);
    EnqueueHandle(const EnqueueHandle& r);
    ~EnqueueHandle();
    EnqueueHandle& operator=(const EnqueueHandle& r);

    // EnqueueHandleImpl methods
    // <none>

private:
    friend class PrivateImplRef<EnqueueHandle>;
};

}} // namespace qpid::broker

#endif // qpid_broker_EnqueueHandle_h_
