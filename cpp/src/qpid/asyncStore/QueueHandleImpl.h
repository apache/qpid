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
 * \file QueueHandleImpl.h
 */

#ifndef qpid_asyncStore_QueueHandleImpl_h_
#define qpid_asyncStore_QueueHandleImpl_h_

#include "qpid/RefCounted.h"
#include "qpid/types/Variant.h"

#include <string>

namespace qpid {
namespace asyncStore {

class QueueHandleImpl : public virtual qpid::RefCounted
{
public:
    QueueHandleImpl(const std::string& name,
                    const qpid::types::Variant::Map& opts);
    virtual ~QueueHandleImpl();

    const std::string& getName() const;

protected:
    const std::string m_name;
    const qpid::types::Variant::Map& m_opts;

};

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_QueueHandleImpl_h_
