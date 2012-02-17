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
#include "qpid/broker/QueueReplicator.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

QueueReplicator::QueueReplicator(const std::string& name, boost::shared_ptr<Queue> q) : Exchange(name, 0, 0), queue(q), current(queue->getPosition()) {}
QueueReplicator::~QueueReplicator() {}

namespace {
const std::string DEQUEUE_EVENT("dequeue-event");
const std::string REPLICATOR("qpid.replicator-");
}

void QueueReplicator::route(Deliverable& msg, const std::string& key, const qpid::framing::FieldTable* /*args*/)
{
    if (key == DEQUEUE_EVENT) {
        std::string content;
        msg.getMessage().getFrames().getContent(content);
        qpid::framing::Buffer buffer(const_cast<char*>(content.c_str()), content.size());
        qpid::framing::SequenceSet latest;
        latest.decode(buffer);

        //TODO: should be able to optimise the following
        for (qpid::framing::SequenceSet::iterator i = latest.begin(); i != latest.end(); i++) {
            if (current < *i) {
                //haven't got that far yet, record the dequeue
                dequeued.add(*i);
                QPID_LOG(debug, "Recording dequeue of message at " << *i << " from " << queue->getName());
            } else {
                QueuedMessage message;
                if (queue->acquireMessageAt(*i, message)) {
                    queue->dequeue(0, message);
                    QPID_LOG(info, "Dequeued message at " << *i << " from " << queue->getName());
                } else {
                    QPID_LOG(error, "Unable to dequeue message at " << *i << " from " << queue->getName());
                }
            }
        }
    } else {
        //take account of any gaps in sequence created by messages
        //dequeued before our subscription reached them
        while (dequeued.contains(++current)) {
            dequeued.remove(current);
            QPID_LOG(debug, "Skipping dequeued message at " << current << " from " << queue->getName());
            queue->setPosition(current);
        }
        QPID_LOG(info, "Enqueued message on " << queue->getName() << "; currently at " << current);
        msg.deliverTo(queue);
    }
}

bool QueueReplicator::isReplicatingLink(const std::string& name)
{
    return name.find(REPLICATOR) == 0;
}

boost::shared_ptr<Exchange> QueueReplicator::create(const std::string& target, QueueRegistry& queues)
{
    boost::shared_ptr<Exchange> exchange;
    if (isReplicatingLink(target)) {
        std::string queueName = target.substr(REPLICATOR.size());
        boost::shared_ptr<Queue> queue = queues.find(queueName);
        if (!queue) {
            QPID_LOG(warning, "Unable to create replicator, can't find " << queueName);
        } else {
            //TODO: need to cache the replicator
            QPID_LOG(info, "Creating replicator for " << queueName);
            exchange.reset(new QueueReplicator(target, queue));
        }
    }
    return exchange;
}

bool QueueReplicator::initReplicationSettings(const std::string& target, QueueRegistry& queues, qpid::framing::FieldTable& settings)
{
    if (isReplicatingLink(target)) {
        std::string queueName = target.substr(REPLICATOR.size());
        boost::shared_ptr<Queue> queue = queues.find(queueName);
        if (queue) {
            settings.setInt("qpid.replicating-subscription", 1);
            settings.setInt("qpid.high_sequence_number", queue->getPosition());
            qpid::framing::SequenceNumber oldest;
            if (queue->getOldest(oldest)) {
                settings.setInt("qpid.low_sequence_number", oldest);
            }
        }
        return true;
    } else {
        return false;
    }
}

bool QueueReplicator::bind(boost::shared_ptr<Queue>, const std::string&, const qpid::framing::FieldTable*) { return false; }
bool QueueReplicator::unbind(boost::shared_ptr<Queue>, const std::string&, const qpid::framing::FieldTable*) { return false; }
bool QueueReplicator::isBound(boost::shared_ptr<Queue>, const std::string* const, const qpid::framing::FieldTable* const) { return false; }

const std::string QueueReplicator::typeName("queue-replicator");

std::string QueueReplicator::getType() const
{
    return typeName;
}

}} // namespace qpid::broker
