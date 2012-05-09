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
 * \file EnqueueHandleImpl.h
 */

#ifndef qpid_asyncStore_EnqueueHandleImpl_h_
#define qpid_asyncStore_EnqueueHandleImpl_h_

#include "qpid/RefCounted.h"

namespace qpid {

namespace broker {
class MessageHandle;
class QueueHandle;
}

namespace asyncStore {

class EnqueueHandleImpl : public virtual qpid::RefCounted
{
public:
    EnqueueHandleImpl(qpid::broker::MessageHandle& msgHandle,
                      qpid::broker::QueueHandle& queueHandle);
    virtual ~EnqueueHandleImpl();
protected:
    qpid::broker::MessageHandle& m_msgHandle;
    qpid::broker::QueueHandle& m_queueHandle;
};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_EnqueueHandleImpl_h_
