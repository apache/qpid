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
 * \file EventHandle.h
 */

#ifndef qpid_broker_EventHandle_h_
#define qpid_broker_EventHandle_h_

#include "qpid/asyncStore/AsyncStoreHandle.h"
#include "qpid/broker/Handle.h"

#include <string>

namespace qpid {
namespace asyncStore {
class EventHandleImpl;
}
namespace broker {

class EventHandle : public Handle<qpid::asyncStore::EventHandleImpl>,
                    public qpid::asyncStore::AsyncStoreHandle
{
public:
    EventHandle(qpid::asyncStore::EventHandleImpl* p = 0);
    EventHandle(const EventHandle& r);
    ~EventHandle();
    EventHandle& operator=(const EventHandle& r);

    // --- EventHandleImpl methods ---
    const std::string& getKey() const;

private:
    friend class PrivateImplRef<EventHandle>;
};

}} // namespace qpid::broker

#endif // qpid_broker_EventHandle_h_
