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
#include <TxBuffer.h>

using std::mem_fun;
using namespace qpid::broker;

bool TxBuffer::prepare(TransactionalStore* const store)
{
    std::auto_ptr<TransactionContext> ctxt;
    if(store) ctxt = store->begin();
    for(op_iterator i = ops.begin(); i < ops.end(); i++){
        if(!(*i)->prepare(ctxt.get())){
            if(store) store->abort(ctxt.get());
            return false;
        }
    }
    if(store) store->commit(ctxt.get());
    return true;
}

void TxBuffer::commit()
{
    for_each(ops.begin(), ops.end(), mem_fun(&TxOp::commit));
    ops.clear();
}

void TxBuffer::rollback()
{
    for_each(ops.begin(), ops.end(), mem_fun(&TxOp::rollback));
    ops.clear();
}

void TxBuffer::enlist(TxOp* const op)
{
    ops.push_back(op);
}
