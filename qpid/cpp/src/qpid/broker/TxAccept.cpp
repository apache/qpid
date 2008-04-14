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
#include "TxAccept.h"
#include "qpid/log/Statement.h"

using std::bind1st;
using std::bind2nd;
using std::mem_fun_ref;
using namespace qpid::broker;
using qpid::framing::AccumulatedAck;

TxAccept::TxAccept(AccumulatedAck& _acked, std::list<DeliveryRecord>& _unacked) : 
    acked(_acked), unacked(_unacked) {}

bool TxAccept::prepare(TransactionContext* ctxt) throw()
{
    try{
        //dequeue messages from their respective queues:
        for (ack_iterator i = unacked.begin(); i != unacked.end(); i++) {
            if (i->coveredBy(&acked)) {
                i->dequeue(ctxt);
            }
        }
        return true;
    }catch(const std::exception& e){
        QPID_LOG(error, "Failed to prepare: " << e.what());
        return false;
    }catch(...){
        QPID_LOG(error, "Failed to prepare");
        return false;
    }
}

void TxAccept::commit() throw() 
{
    for (ack_iterator i = unacked.begin(); i != unacked.end(); i++) {
        if (i->coveredBy(&acked)) i->setEnded();
    }

    unacked.remove_if(mem_fun_ref(&DeliveryRecord::isRedundant));
}

void TxAccept::rollback() throw() {}
