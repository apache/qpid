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
#include "qpid/broker/QueueFactory.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/LossyLvq.h"
#include "qpid/broker/LossyQueue.h"
#include "qpid/broker/Lvq.h"
#include "qpid/broker/Messages.h"
#include "qpid/broker/MessageDistributor.h"
#include "qpid/broker/MessageGroupManager.h"
#include "qpid/broker/Fairshare.h"
#include "qpid/broker/MessageDeque.h"
#include "qpid/broker/MessageMap.h"
#include "qpid/broker/PagedQueue.h"
#include "qpid/broker/PriorityQueue.h"
#include "qpid/broker/QueueFlowLimit.h"
#include "qpid/broker/SelfDestructQueue.h"
#include "qpid/broker/ThresholdAlerts.h"
#include "qpid/broker/FifoDistributor.h"
#include "qpid/log/Statement.h"
#include <map>
#include <memory>

namespace qpid {
namespace broker {

QueueFactory::QueueFactory() : broker(0), store(0), parent(0) {}

boost::shared_ptr<Queue> QueueFactory::create(const std::string& name, const QueueSettings& settings)
{
    settings.validate();
    boost::shared_ptr<QueueFlowLimit> flow_ptr(QueueFlowLimit::createLimit(name, settings));

    //1. determine Queue type (i.e. whether we are subclassing Queue)
    boost::shared_ptr<Queue> queue;
    if (settings.dropMessagesAtLimit) {
        // -> if 'ring' policy is in use then subclass
        if (settings.lvqKey.size()) {
            //combination of ring and lvq:
            std::auto_ptr<MessageMap> map(new MessageMap(settings.lvqKey));
            queue = boost::shared_ptr<Queue>(new LossyLvq(name, map, settings, settings.durable ? store : 0, parent, broker));
        } else {
            //simple ring:
            queue = boost::shared_ptr<Queue>(new LossyQueue(name, settings, settings.durable ? store : 0, parent, broker));
        }
    } else if (settings.selfDestructAtLimit) {
        queue = boost::shared_ptr<Queue>(new SelfDestructQueue(name, settings, settings.durable ? store : 0, parent, broker));
    } else if (settings.lvqKey.size()) {
        std::auto_ptr<MessageMap> map(new MessageMap(settings.lvqKey));
        queue = boost::shared_ptr<Queue>(new Lvq(name, map, settings, settings.durable ? store : 0, parent, broker));
    } else {
        queue = boost::shared_ptr<Queue>(new Queue(name, settings, settings.durable ? store : 0, parent, broker));
    }

    //2. determine Messages type (i.e. structure)
    if (settings.priorities) {
        if (settings.defaultFairshare || settings.fairshare.size()) {
            queue->messages = Fairshare::create(settings);
        } else {
            queue->messages = std::auto_ptr<Messages>(new PriorityQueue(settings.priorities));
        }
    } else if (settings.paging) {
        if (!broker) {
            QPID_LOG(warning, "Cannot create paged queue without broker context");
        } else if (!qpid::sys::MemoryMappedFile::isSupported()) {
            QPID_LOG(warning, "Cannot create paged queue; memory mapped file support not available on this platform");
        } else if ( !broker->getPagingDir().isEnabled() ) {
            QPID_LOG(warning, "Cannot create paged queue; no paging directory enabled");
        } else {
            queue->messages = std::auto_ptr<Messages>(new PagedQueue(name, broker->getPagingDir().getPath(),
                                                                     settings.maxPages ? settings.maxPages : DEFAULT_MAX_PAGES,
                                                                     settings.pageFactor ? settings.pageFactor : DEFAULT_PAGE_FACTOR,
                                                                     broker->getProtocolRegistry()));
        }
    } else if (settings.lvqKey.empty()) {//LVQ already handled above
        queue->messages = std::auto_ptr<Messages>(new MessageDeque());
    }

    //3. determine MessageDistributor type
    if (settings.groupKey.size()) {
        boost::shared_ptr<MessageGroupManager> mgm(MessageGroupManager::create( name, *(queue->messages), settings));
        queue->allocator = mgm;
        queue->getObservers().add(mgm);
    } else {
        queue->allocator = boost::shared_ptr<MessageDistributor>(new FifoDistributor( *(queue->messages) ));
    }


    //4. threshold event config
    if (broker && broker->getManagementAgent()) {
        ThresholdAlerts::observe(*queue, *(broker->getManagementAgent()), settings, broker->getQueueThresholdEventRatio());
    }
    //5. flow control config
    if (flow_ptr) {
	flow_ptr->observe(*queue);
    }

    return queue;
}

void QueueFactory::setBroker(Broker* b)
{
    broker = b;
}
Broker* QueueFactory::getBroker()
{
    return broker;
}
void QueueFactory::setStore (MessageStore* s)
{
    store = s;
}
MessageStore* QueueFactory::getStore() const
{
    return store;
}
void QueueFactory::setParent(management::Manageable* p)
{
    parent = p;
}

}} // namespace qpid::broker
