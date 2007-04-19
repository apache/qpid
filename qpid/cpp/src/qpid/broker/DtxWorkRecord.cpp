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
#include "DtxWorkRecord.h"
#include <boost/format.hpp>
#include <boost/mem_fn.hpp>
using boost::mem_fn;

using namespace qpid::broker;

DtxWorkRecord::DtxWorkRecord(const std::string& _xid, TransactionalStore* const _store) : xid(_xid), store(_store) {}

DtxWorkRecord::~DtxWorkRecord() {}

bool DtxWorkRecord::prepare()
{
    checkCompletion();
    txn = store->begin(xid);
    if (prepare(txn.get())) {
        store->prepare(*txn);
        return true;
    } else {
        abort();
        return false;
    }
}

bool DtxWorkRecord::prepare(TransactionContext* _txn)
{
    bool succeeded(true);
    for (Work::iterator i = work.begin(); succeeded && i != work.end(); i++) {
        succeeded = (*i)->prepare(_txn);
    }
    return succeeded;
}

void DtxWorkRecord::commit()
{
    checkCompletion();
    if (txn.get()) {
        //already prepared
        store->commit(*txn);
        txn.reset();

        for_each(work.begin(), work.end(), mem_fn(&TxBuffer::commit));
    } else {
        //1pc commit optimisation, don't need a 2pc transaction context:
        std::auto_ptr<TransactionContext> localtxn = store->begin();
        if (prepare(localtxn.get())) {
            store->commit(*localtxn);
        } else {
            store->abort(*localtxn);
            abort();
        }
    }
}

void DtxWorkRecord::rollback()
{
    checkCompletion();
    abort();
}

void DtxWorkRecord::add(DtxBuffer::shared_ptr ops)
{
    work.push_back(ops);
}

void DtxWorkRecord::checkCompletion()
{
    if (!completed) {
        //iterate through all DtxBuffers and ensure they are all ended
        for (Work::iterator i = work.begin(); i != work.end(); i++) {
            if (!(*i)->isEnded()) {
                throw ConnectionException(503, boost::format("Branch with xid %1% not completed!") % xid);
            }
        }
        completed = true;
    }
}

void DtxWorkRecord::abort()
{
    if (txn.get()) {
        store->abort(*txn);
        txn.reset();
    }
    for_each(work.begin(), work.end(), mem_fn(&TxBuffer::rollback));

}
