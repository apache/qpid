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
#include "qpid/broker/TxAccept.h"
#include "qpid/broker/TransactionObserver.h"
#include "qpid/broker/Queue.h"
#include "qpid/log/Statement.h"

using std::bind1st;
using std::bind2nd;
using std::mem_fun_ref;
using namespace qpid::broker;
using qpid::framing::SequenceSet;
using qpid::framing::SequenceNumber;


TxAccept::TxAccept(const SequenceSet& _acked, DeliveryRecords& _unacked) :
    acked(_acked), unacked(_unacked)
{}

void TxAccept::each(boost::function<void(DeliveryRecord&)> f) {
    DeliveryRecords::iterator dr = unacked.begin();
    SequenceSet::iterator seq = acked.begin();
    while(dr != unacked.end() && seq != acked.end()) {
        if (dr->getId() == *seq) {
            f(*dr);
            ++dr;
            ++seq;
        }
        else if (dr->getId() < *seq) ++dr;
        else if (dr->getId() > *seq) ++seq;
    }
}

bool TxAccept::prepare(TransactionContext* ctxt) throw()
{
    try{
        each(bind(&DeliveryRecord::dequeue, _1, ctxt));
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
    try {
        each(bind(&DeliveryRecord::committed, _1));
        each(bind(&DeliveryRecord::setEnded, _1));
        //now remove if isRedundant():
        if (!acked.empty()) {
            AckRange r = DeliveryRecord::findRange(unacked, acked.front(), acked.back());
            DeliveryRecords::iterator removed =
                remove_if(r.start, r.end, mem_fun_ref(&DeliveryRecord::isRedundant));
            unacked.erase(removed, r.end);
        }
    } catch (const std::exception& e) {
        QPID_LOG(error, "Failed to commit: " << e.what());
    } catch(...) {
        QPID_LOG(error, "Failed to commit (unknown error)");
    }
}

void TxAccept::rollback() throw() {}

namespace {
void callObserverDR(boost::shared_ptr<TransactionObserver> observer, DeliveryRecord& dr) {
    observer->dequeue(dr.getQueue(), dr.getMessageId(), dr.getReplicationId());
}
} // namespace

void TxAccept::callObserver(const ObserverPtr& observer) {
    each(boost::bind(&callObserverDR, observer, _1));
}
