/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "qpid/broker/TxBuffer.h"

using std::mem_fun;
using namespace qpid::broker;

bool TxBuffer::prepare(TransactionalStore* const store){
    TransactionContext* ctxt(0);
    if(store) ctxt = store->begin();
    for(op_iterator i = ops.begin(); i < ops.end(); i++){
        if(!(*i)->prepare(ctxt)){
            if(store) store->abort(ctxt);
            return false;
        }
    }
    if(store) store->commit(ctxt);
    return true;
}

void TxBuffer::commit(){
    for_each(ops.begin(), ops.end(), mem_fun(&TxOp::commit));
}

void TxBuffer::rollback(){
    for_each(ops.begin(), ops.end(), mem_fun(&TxOp::rollback));
}

void TxBuffer::enlist(TxOp* const op){
    ops.push_back(op);
}
