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
 * \file QueueHandle.h
 */

#ifndef qpid_broker_QueueHandle_h_
#define qpid_broker_QueueHandle_h_

#include "Handle.h"

#include "qpid/asyncStore/AsyncStoreHandle.h"

#include <string>

namespace qpid {
namespace asyncStore {
class QueueHandleImpl;
}
namespace broker {

class QueueHandle : public Handle<qpid::asyncStore::QueueHandleImpl>,
                    public qpid::asyncStore::AsyncStoreHandle
{
public:
    QueueHandle(qpid::asyncStore::QueueHandleImpl* p = 0);
    QueueHandle(const QueueHandle& r);
    ~QueueHandle();
    QueueHandle& operator=(const QueueHandle& r);

    // --- QueueHandleImpl methods ---
    const std::string& getName() const;

private:
    friend class PrivateImplRef<QueueHandle>;
};

}} // namespace qpid::broker

#endif // qpid_broker_QueueHandle_h_
