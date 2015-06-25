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
#include "qpid/broker/RecoveryManagerImpl.h"

#include "qpid/broker/Message.h"
#include "qpid/broker/PersistableMessage.h"
#include "qpid/broker/PersistableObject.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Protocol.h"
#include "qpid/broker/RecoverableMessageImpl.h"
#include "qpid/broker/RecoveredEnqueue.h"
#include "qpid/broker/RecoveredDequeue.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/reply_exceptions.h"

using boost::dynamic_pointer_cast;
using boost::intrusive_ptr;
using std::string;

namespace qpid {
namespace broker {

RecoveryManagerImpl::RecoveryManagerImpl(QueueRegistry& _queues, ExchangeRegistry& _exchanges, LinkRegistry& _links,
                                         DtxManager& _dtxMgr, ProtocolRegistry& p, RecoveredObjects& o)
    : queues(_queues), exchanges(_exchanges), links(_links), dtxMgr(_dtxMgr), protocols(p), objects(o) {}

RecoveryManagerImpl::~RecoveryManagerImpl() {}

class RecoverableQueueImpl : public RecoverableQueue
{
    Queue::shared_ptr queue;
public:
    RecoverableQueueImpl(const boost::shared_ptr<Queue>& _queue) : queue(_queue) {}
    ~RecoverableQueueImpl() {};
    void setPersistenceId(uint64_t id);
	uint64_t getPersistenceId() const;
    const std::string& getName() const;
    void setExternalQueueStore(ExternalQueueStore* inst);
    ExternalQueueStore* getExternalQueueStore() const;
    const QueueSettings& getSettings() const;
    void addArgument(const std::string& key, const types::Variant& value);
    void recover(RecoverableMessage::shared_ptr msg);
    void enqueue(boost::intrusive_ptr<DtxBuffer> buffer, RecoverableMessage::shared_ptr msg);
    void dequeue(boost::intrusive_ptr<DtxBuffer> buffer, RecoverableMessage::shared_ptr msg);

};

class RecoverableExchangeImpl : public RecoverableExchange
{
    Exchange::shared_ptr exchange;
    QueueRegistry& queues;
public:
    RecoverableExchangeImpl(Exchange::shared_ptr _exchange, QueueRegistry& _queues) : exchange(_exchange), queues(_queues) {}
    void setPersistenceId(uint64_t id);
    void bind(const std::string& queue, const std::string& routingKey, qpid::framing::FieldTable& args);
    string getName() const { return exchange->getName(); }
};

class RecoverableConfigImpl : public RecoverableConfig
{
    Link::shared_ptr   link;
    Bridge::shared_ptr bridge;
public:
    RecoverableConfigImpl(Link::shared_ptr _link)     : link(_link)     {}
    RecoverableConfigImpl(Bridge::shared_ptr _bridge) : bridge(_bridge) {}
    void setPersistenceId(uint64_t id);
};

class RecoverableTransactionImpl : public RecoverableTransaction
{
    boost::intrusive_ptr<DtxBuffer> buffer;
public:
    RecoverableTransactionImpl(boost::intrusive_ptr<DtxBuffer> _buffer) : buffer(_buffer) {}
    void enqueue(RecoverableQueue::shared_ptr queue, RecoverableMessage::shared_ptr message);
    void dequeue(RecoverableQueue::shared_ptr queue, RecoverableMessage::shared_ptr message);
};

RecoverableExchange::shared_ptr RecoveryManagerImpl::recoverExchange(framing::Buffer& buffer)
{
    Exchange::shared_ptr e = Exchange::decode(exchanges, buffer);
    if (e) {
        return RecoverableExchange::shared_ptr(new RecoverableExchangeImpl(e, queues));
    } else {
        return RecoverableExchange::shared_ptr();
    }
}

RecoverableQueue::shared_ptr RecoveryManagerImpl::recoverQueue(framing::Buffer& buffer)
{
    Queue::shared_ptr queue = Queue::restore(queues, buffer);
    try {
        Exchange::shared_ptr exchange = exchanges.getDefault();
        if (exchange) {
            exchange->bind(queue, queue->getName(), 0);
            queue->bound(exchange->getName(), queue->getName(), framing::FieldTable());
        }
    } catch (const framing::NotFoundException& /*e*/) {
        //assume no default exchange has been declared
    }
    return RecoverableQueue::shared_ptr(new RecoverableQueueImpl(queue));
}

RecoverableMessage::shared_ptr RecoveryManagerImpl::recoverMessage(framing::Buffer& buffer)
{
    RecoverableMessage::shared_ptr m = protocols.recover(buffer);
    return m;
}

RecoverableTransaction::shared_ptr RecoveryManagerImpl::recoverTransaction(const std::string& xid,
                                                                           std::auto_ptr<TPCTransactionContext> txn)
{
    boost::intrusive_ptr<DtxBuffer> buffer(new DtxBuffer());
    dtxMgr.recover(xid, txn, buffer);
    return RecoverableTransaction::shared_ptr(new RecoverableTransactionImpl(buffer));
}

RecoverableConfig::shared_ptr RecoveryManagerImpl::recoverConfig(framing::Buffer& buffer)
{
    string kind;
    uint32_t p = buffer.getPosition();
    buffer.getShortString (kind);
    buffer.setPosition(p);

    if (Link::isEncodedLink(kind))
        return RecoverableConfig::shared_ptr(new RecoverableConfigImpl(Link::decode (links, buffer)));
    else if (Bridge::isEncodedBridge(kind))
        return RecoverableConfig::shared_ptr(new RecoverableConfigImpl(Bridge::decode (links, buffer)));

    return objects.recover(buffer);
}

void RecoveryManagerImpl::recoveryComplete()
{
    //notify all queues and exchanges
    queues.eachQueue(boost::bind(&Queue::recoveryComplete, _1, boost::ref(exchanges)));
    exchanges.eachExchange(boost::bind(&Exchange::recoveryComplete, _1, boost::ref(exchanges)));
}

RecoverableMessageImpl:: RecoverableMessageImpl(const Message& _msg) : msg(_msg) {}

bool RecoverableMessageImpl::loadContent(uint64_t /*available*/)
{
    return true;
}

void RecoverableMessageImpl::decodeContent(framing::Buffer& buffer)
{
    msg.getPersistentContext()->decodeContent(buffer);
}

void RecoverableMessageImpl::recover(Queue::shared_ptr queue)
{
    queue->recover(msg);
}

void RecoverableMessageImpl::setPersistenceId(uint64_t id)
{
    msg.getPersistentContext()->setPersistenceId(id);
}

void RecoverableMessageImpl::setRedelivered()
{
    msg.deliver();//increment delivery count (but at present that isn't recorded durably)
}

void RecoverableMessageImpl::computeExpiration()
{
    msg.getSharedState().computeExpiration();
}

Message RecoverableMessageImpl::getMessage()
{
    return msg;
}

void RecoverableQueueImpl::recover(RecoverableMessage::shared_ptr msg)
{
    dynamic_pointer_cast<RecoverableMessageImpl>(msg)->recover(queue);
}

void RecoverableQueueImpl::setPersistenceId(uint64_t id)
{
    queue->setPersistenceId(id);
}

uint64_t RecoverableQueueImpl::getPersistenceId() const
{
	return queue->getPersistenceId();
}

const std::string& RecoverableQueueImpl::getName() const
{
    return queue->getName();
}

void RecoverableQueueImpl::setExternalQueueStore(ExternalQueueStore* inst)
{
    queue->setExternalQueueStore(inst);
}

ExternalQueueStore* RecoverableQueueImpl::getExternalQueueStore() const
{
	return queue->getExternalQueueStore();
}

const QueueSettings& RecoverableQueueImpl::getSettings() const
{
    return queue->getSettings();
}

void RecoverableQueueImpl::addArgument(const std::string& key, const types::Variant& value)
{
    queue->addArgument(key, value);
}

void RecoverableExchangeImpl::setPersistenceId(uint64_t id)
{
    exchange->setPersistenceId(id);
}

void RecoverableConfigImpl::setPersistenceId(uint64_t id)
{
    if (link.get())
        link->setPersistenceId(id);
    else if (bridge.get())
        bridge->setPersistenceId(id);
}

void RecoverableExchangeImpl::bind(const string& queueName,
                                   const string& key,
                                   framing::FieldTable& args)
{
    Queue::shared_ptr queue = queues.find(queueName);
    exchange->bind(queue, key, &args);
    queue->bound(exchange->getName(), key, args);
}

void RecoverableMessageImpl::dequeue(boost::intrusive_ptr<DtxBuffer> buffer, Queue::shared_ptr queue)
{
    buffer->enlist(TxOp::shared_ptr(new RecoveredDequeue(queue, msg)));
}

void RecoverableMessageImpl::enqueue(boost::intrusive_ptr<DtxBuffer> buffer, Queue::shared_ptr queue)
{
    buffer->enlist(TxOp::shared_ptr(new RecoveredEnqueue(queue, msg)));
}

void RecoverableQueueImpl::dequeue(boost::intrusive_ptr<DtxBuffer> buffer, RecoverableMessage::shared_ptr message)
{
    dynamic_pointer_cast<RecoverableMessageImpl>(message)->dequeue(buffer, queue);
}

void RecoverableQueueImpl::enqueue(boost::intrusive_ptr<DtxBuffer> buffer, RecoverableMessage::shared_ptr message)
{
    dynamic_pointer_cast<RecoverableMessageImpl>(message)->enqueue(buffer, queue);
}

void RecoverableTransactionImpl::dequeue(RecoverableQueue::shared_ptr queue, RecoverableMessage::shared_ptr message)
{
    dynamic_pointer_cast<RecoverableQueueImpl>(queue)->dequeue(buffer, message);
}

void RecoverableTransactionImpl::enqueue(RecoverableQueue::shared_ptr queue, RecoverableMessage::shared_ptr message)
{
    dynamic_pointer_cast<RecoverableQueueImpl>(queue)->enqueue(buffer, message);
}

}}
