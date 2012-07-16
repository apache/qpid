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

#ifndef qpid_broker_TxnHandle_h_
#define qpid_broker_TxnHandle_h_

#include "Handle.h"

#include "qpid/asyncStore/AsyncStoreHandle.h"

#include <string>

namespace qpid {
namespace asyncStore {
class TxnHandleImpl;
}
namespace broker {

class TxnHandle : public Handle<qpid::asyncStore::TxnHandleImpl>,
                  public qpid::asyncStore::AsyncStoreHandle
{
public:
    TxnHandle(qpid::asyncStore::TxnHandleImpl* p = 0);
    TxnHandle(const TxnHandle& r);
    ~TxnHandle();
    TxnHandle& operator=(const TxnHandle& r);

    // TxnHandleImpl methods
    const std::string& getXid() const;
    bool is2pc() const;
    void incrOpCnt();
    void decrOpCnt();

private:
    friend class PrivateImplRef<TxnHandle>;
};

}} // namespace qpid::broker

#endif // qpid_broker_TxnHandle_h_
