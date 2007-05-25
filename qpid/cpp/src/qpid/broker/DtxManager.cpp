/*
 *
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
 *
 */
#include "DtxManager.h"
#include <boost/format.hpp>

using namespace qpid::broker;

DtxManager::DtxManager(TransactionalStore* const _store) : store(_store) {}

DtxManager::~DtxManager() {}

void DtxManager::start(std::string xid, DtxBuffer::shared_ptr ops)
{
    getOrCreateWork(xid)->add(ops);
}

void DtxManager::recover(std::string xid, std::auto_ptr<TPCTransactionContext> txn, DtxBuffer::shared_ptr ops)
{
    getOrCreateWork(xid)->recover(txn, ops);
}

void DtxManager::prepare(const std::string& xid) 
{ 
    getWork(xid)->prepare();
}

void DtxManager::commit(const std::string& xid) 
{ 
    getWork(xid)->commit();
}

void DtxManager::rollback(const std::string& xid) 
{ 
    getWork(xid)->rollback();
}

DtxManager::WorkMap::iterator DtxManager::getWork(const std::string& xid)
{
    WorkMap::iterator i = work.find(xid);
    if (i == work.end()) {
        throw ConnectionException(503, boost::format("Unrecognised xid %1%!") % xid);
    }
    return i;
}

DtxManager::WorkMap::iterator DtxManager::getOrCreateWork(std::string& xid)
{
    WorkMap::iterator i = work.find(xid);
    if (i == work.end()) {
        i = work.insert(xid, new DtxWorkRecord(xid, store)).first;
    }
    return i;
}
