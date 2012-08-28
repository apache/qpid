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
 * \file AsyncResultHandle.h
 */

#ifndef qpid_broker_AsyncResultHandle_h_
#define qpid_broker_AsyncResultHandle_h_

#include "qpid/broker/Handle.h"

#include <boost/shared_ptr.hpp>
#include <string>

namespace qpid {
namespace broker {
class AsyncResultHandleImpl;
class BrokerAsyncContext;

class AsyncResultHandle : public Handle<AsyncResultHandleImpl>
{
public:
    AsyncResultHandle(AsyncResultHandleImpl* p = 0);
    AsyncResultHandle(const AsyncResultHandle& r);
    virtual ~AsyncResultHandle();
    AsyncResultHandle& operator=(const AsyncResultHandle& r);

    // AsyncResultHandleImpl methods

    int getErrNo() const;
    std::string getErrMsg() const;
    boost::shared_ptr<BrokerAsyncContext> getBrokerAsyncContext() const;
    void invokeAsyncResultCallback() const;

private:
    friend class PrivateImplRef<AsyncResultHandle>;
};

}} // namespace qpid::broker

#endif // qpid_broker_AsyncResultHandle_h_
