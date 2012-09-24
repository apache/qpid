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
 * \file ConfigAsyncContext.h
 */

#ifndef qpid_broker_ConfigAsyncContext_h_
#define qpid_broker_ConfigAsyncContext_h_

#include "AsyncStore.h"

namespace qpid {
namespace broker {
class AsyncResultHandle;
class AsyncResultQueue;

typedef void (*AsyncResultCallback)(const AsyncResultHandle* const);

class ConfigAsyncContext: public qpid::broker::BrokerAsyncContext
{
public:
    ConfigAsyncContext(AsyncResultCallback rcb,
                       AsyncResultQueue* const arq);
    virtual ~ConfigAsyncContext();
    virtual AsyncResultQueue* getAsyncResultQueue() const;
    virtual void invokeCallback(const AsyncResultHandle* const) const;

private:
    AsyncResultCallback m_rcb;
    AsyncResultQueue* const m_arq;
};

}} // namespace qpid::broker

#endif // qpid_broker_ConfigAsyncContext_h_
