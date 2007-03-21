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
#include <TxAck.h>

using std::bind1st;
using std::bind2nd;
using std::mem_fun_ref;
using namespace qpid::broker;

TxAck::TxAck(AccumulatedAck& _acked, std::list<DeliveryRecord>& _unacked, const std::string* const _xid) : 
    acked(_acked), unacked(_unacked), xid(_xid){

}

bool TxAck::prepare(TransactionContext* ctxt) throw(){
    try{
        //dequeue all acked messages from their queues
        for (ack_iterator i = unacked.begin(); i != unacked.end(); i++) {
            if (i->coveredBy(&acked)) {
                i->discard(ctxt, xid);
            }
        }
        return true;
    }catch(...){
        std::cout << "TxAck::prepare() - Failed to prepare" << std::endl;
        return false;
    }
}

void TxAck::commit() throw(){
    //remove all acked records from the list
    unacked.remove_if(bind2nd(mem_fun_ref(&DeliveryRecord::coveredBy), &acked));
}

void TxAck::rollback() throw(){
}
