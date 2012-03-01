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

#include "Transaction.h"
#include "Messages.h"

namespace qpid {
namespace store {
namespace ms_clfs {

Transaction::~Transaction()
{
    // Transactions that are recovered then found to be deleted get destroyed
    // but need not be logged.
    if (state != TRANS_DELETED)
        log->deleteTransaction(id);
}

void
Transaction::enroll(uint64_t msgId)
{
    qpid::sys::ScopedWlock<qpid::sys::RWlock> l(enrollLock);
    enrolledMessages.push_back(msgId);
}

void
Transaction::unenroll(uint64_t msgId)
{
    qpid::sys::ScopedWlock<qpid::sys::RWlock> l(enrollLock);
    for (std::vector<uint64_t>::iterator i = enrolledMessages.begin();
         i < enrolledMessages.end();
         ++i) {
        if (*i == msgId) {
            enrolledMessages.erase(i);
            break;
        }
    }
}

void
Transaction::abort(Messages& messages)
{
    log->recordAbort(id);
    for (size_t i = 0; i < enrolledMessages.size(); ++i)
        messages.abort(enrolledMessages[i], shared_from_this());
    state = TRANS_ABORTED;
}

void
Transaction::commit(Messages& messages)
{
    log->recordCommit(id);
    for (size_t i = 0; i < enrolledMessages.size(); ++i)
        messages.commit(enrolledMessages[i], shared_from_this());
    state = TRANS_COMMITTED;
}

void
TPCTransaction::prepare(void)
{
    log->recordPrepare(id);
    state = TRANS_PREPARED;
}

}}}  // namespace qpid::store::ms_clfs
