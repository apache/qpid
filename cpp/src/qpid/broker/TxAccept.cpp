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
#include "qpid/log/Statement.h"

using std::bind1st;
using std::bind2nd;
using std::mem_fun_ref;
using namespace qpid::broker;
using qpid::framing::SequenceSet;
using qpid::framing::SequenceNumber;


TxAccept::TxAccept(const SequenceSet& _acked, DeliveryRecords& _unacked) :
    acked(_acked), unacked(_unacked)
{
    for(SequenceSet::RangeIterator i = acked.rangesBegin(); i != acked.rangesEnd(); ++i)
        ranges.push_back(DeliveryRecord::findRange(unacked, i->first(), i->last()));
}

void TxAccept::each(boost::function<void(DeliveryRecord&)> f) {
    for(AckRanges::iterator i = ranges.begin(); i != ranges.end(); ++i)
        for_each(i->start, i->end, f);
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
        if (!ranges.empty()) {
            DeliveryRecords::iterator begin = ranges.front().start;
            DeliveryRecords::iterator end = ranges.back().end;
            DeliveryRecords::iterator removed =
                remove_if(begin, end, mem_fun_ref(&DeliveryRecord::isRedundant));
            unacked.erase(removed, end);
        }
    } catch (const std::exception& e) {
        QPID_LOG(error, "Failed to commit: " << e.what());
    } catch(...) {
        QPID_LOG(error, "Failed to commit (unknown error)");
    }
}

void TxAccept::rollback() throw() {}
