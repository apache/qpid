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
 * \file TxnHandle.h
 */

#ifndef qpid_broker_TxnHandleImpl_h_
#define qpid_broker_TxnHandleImpl_h_

#include "IdHandle.h"

#include "qpid/asyncStore/TxnHandleImpl.h"
#include "qpid/messaging/Handle.h"

namespace qpid {
namespace broker {

class TxnHandle : public qpid::messaging::Handle<qpid::asyncStore::TxnHandleImpl>, public IdHandle
{
public:
    TxnHandle(qpid::asyncStore::TxnHandleImpl* p = 0);
    TxnHandle(const TxnHandle& r);
    ~TxnHandle();
    TxnHandle& operator=(const TxnHandle& r);

    // TxnHandleImpl methods
    const std::string& getXid() const;
    bool is2pc() const;

private:
    typedef qpid::asyncStore::TxnHandleImpl Impl;
    Impl* impl;
    friend class qpid::messaging::PrivateImplRef<TxnHandle>;
};

}} // namespace qpid::broker

#endif // qpid_broker_TxnHandleImpl_h_
