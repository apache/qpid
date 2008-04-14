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

#include "NullMessageStore.h"
#include "RecoveryManager.h"
#include "qpid/log/Statement.h"

#include <iostream>

using boost::intrusive_ptr;

namespace qpid{
namespace broker{

const std::string nullxid = "";

class DummyCtxt : public TPCTransactionContext 
{
    const std::string xid;
public:
    DummyCtxt(const std::string& _xid) : xid(_xid) {}
    static std::string getXid(TransactionContext& ctxt) 
    {
        DummyCtxt* c(dynamic_cast<DummyCtxt*>(&ctxt));
        return c ? c->xid : nullxid;
    }
};

}
}

using namespace qpid::broker;

NullMessageStore::NullMessageStore(bool _warn) : warn(_warn){}

bool NullMessageStore::init(const Options* /*options*/) {return true;}

void NullMessageStore::create(PersistableQueue& queue, const framing::FieldTable& /*args*/)
{
    QPID_LOG(info, "Queue '" << queue.getName() 
             << "' will not be durable. Persistence not enabled.");
}

void NullMessageStore::destroy(PersistableQueue&)
{
}

void NullMessageStore::create(const PersistableExchange& exchange, const framing::FieldTable& /*args*/)
{
    QPID_LOG(info, "Exchange'" << exchange.getName() 
             << "' will not be durable. Persistence not enabled.");
}

void NullMessageStore::destroy(const PersistableExchange& )
{}

void NullMessageStore::bind(const PersistableExchange&, const PersistableQueue&, const std::string&, const framing::FieldTable&){}

void NullMessageStore::unbind(const PersistableExchange&, const PersistableQueue&, const std::string&, const framing::FieldTable&){}

void NullMessageStore::recover(RecoveryManager&)
{
    QPID_LOG(info, "Persistence not enabled, no recovery attempted.");
}

void NullMessageStore::stage(intrusive_ptr<PersistableMessage>&)
{
    QPID_LOG(info, "Can't stage message. Persistence not enabled.");
}

void NullMessageStore::destroy(PersistableMessage&)
{
}

void NullMessageStore::appendContent(intrusive_ptr<const PersistableMessage>&, const string&)
{
    QPID_LOG(info, "Can't append content. Persistence not enabled.");
}

void NullMessageStore::loadContent(const qpid::broker::PersistableQueue&, intrusive_ptr<const PersistableMessage>&, string&, uint64_t, uint32_t)
{
    QPID_LOG(info, "Can't load content. Persistence not enabled.");
}

void NullMessageStore::enqueue(TransactionContext*, intrusive_ptr<PersistableMessage>& msg, const PersistableQueue& queue)
{
    msg->enqueueComplete(); 
    QPID_LOG(info, "Message is not durably recorded on '" << queue.getName() << "'. Persistence not enabled.");
}

void NullMessageStore::dequeue(TransactionContext*, intrusive_ptr<PersistableMessage>& msg, const PersistableQueue&)
{
    msg->dequeueComplete();
}

void NullMessageStore::flush(const qpid::broker::PersistableQueue&)
{
}

u_int32_t NullMessageStore::outstandingQueueAIO(const PersistableQueue& )
{
    return 0;
}

std::auto_ptr<TransactionContext> NullMessageStore::begin()
{
    return std::auto_ptr<TransactionContext>();
}

std::auto_ptr<TPCTransactionContext> NullMessageStore::begin(const std::string& xid)
{
    return std::auto_ptr<TPCTransactionContext>(new DummyCtxt(xid));
}

void NullMessageStore::prepare(TPCTransactionContext& ctxt)
{
    prepared.insert(DummyCtxt::getXid(ctxt));
}

void NullMessageStore::commit(TransactionContext& ctxt)
{
    prepared.erase(DummyCtxt::getXid(ctxt));
}

void NullMessageStore::abort(TransactionContext& ctxt)
{
    prepared.erase(DummyCtxt::getXid(ctxt));
}

void NullMessageStore::collectPreparedXids(std::set<string>& out)
{
    out.insert(prepared.begin(), prepared.end());
}
