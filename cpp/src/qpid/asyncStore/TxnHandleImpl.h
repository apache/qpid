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
 * \file TxnHandleImpl.h
 */

#ifndef qpid_asyncStore_TxnHandleImpl_h_
#define qpid_asyncStore_TxnHandleImpl_h_

#include "qpid/RefCounted.h"

#include <string>

namespace qpid {
namespace asyncStore {

class TxnHandleImpl : public virtual qpid::RefCounted
{
public:
    TxnHandleImpl(const std::string& xid = std::string());
    virtual ~TxnHandleImpl();
    const std::string& getXid() const;
    bool is2pc() const;
protected:
    std::string m_xid;
    bool m_tpcFlag;
};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_TxnHandleImpl_h_
