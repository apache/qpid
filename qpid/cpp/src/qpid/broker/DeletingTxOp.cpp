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
#include <qpid/broker/DeletingTxOp.h>

using namespace qpid::broker;

DeletingTxOp::DeletingTxOp(TxOp* const _delegate) : delegate(_delegate){}

bool DeletingTxOp::prepare(TransactionContext* ctxt) throw(){
    return delegate && delegate->prepare(ctxt);
}

void DeletingTxOp::commit() throw(){
    if(delegate){
        delegate->commit();
        delete delegate;
        delegate = 0;
    }
}

void DeletingTxOp::rollback() throw(){
    if(delegate){
        delegate->rollback();
        delete delegate;
        delegate = 0;
    }
}
