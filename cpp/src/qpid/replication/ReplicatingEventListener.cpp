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
#include "ReplicatingEventListener.h"
#include "constants.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/QueueEvents.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace replication {

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::replication::constants;

void ReplicatingEventListener::handle(QueueEvents::Event event)
{
    switch (event.type) {
      case QueueEvents::ENQUEUE:
        deliverEnqueueMessage(event.msg);
        QPID_LOG(debug, "Queuing 'enqueue' event on " << event.msg.queue->getName() << " for replication");
        break;
      case QueueEvents::DEQUEUE:
        deliverDequeueMessage(event.msg);
        QPID_LOG(debug, "Queuing 'dequeue' event from " << event.msg.queue->getName() << " for replication, (from position "
                 << event.msg.position << ")");
        break;
    }
}

namespace {
const std::string EMPTY;
}

void ReplicatingEventListener::deliverDequeueMessage(const QueuedMessage& dequeued)
{
    FieldTable headers;
    headers.setString(REPLICATION_TARGET_QUEUE, dequeued.queue->getName());
    headers.setInt(REPLICATION_EVENT_SEQNO, ++sequence);
    headers.setInt(REPLICATION_EVENT_TYPE, DEQUEUE);
    headers.setInt(DEQUEUED_MESSAGE_POSITION, dequeued.position);
    boost::intrusive_ptr<Message> msg(createMessage(headers));
    queue->deliver(msg);
}

void ReplicatingEventListener::deliverEnqueueMessage(const QueuedMessage& enqueued)
{
    boost::intrusive_ptr<Message> msg(cloneMessage(*(enqueued.queue), enqueued.payload));
    FieldTable& headers = msg->getProperties<MessageProperties>()->getApplicationHeaders();
    headers.setString(REPLICATION_TARGET_QUEUE, enqueued.queue->getName());
    headers.setInt(REPLICATION_EVENT_SEQNO, ++sequence);
    headers.setInt(REPLICATION_EVENT_TYPE, ENQUEUE);
    queue->deliver(msg);
}

boost::intrusive_ptr<Message> ReplicatingEventListener::createMessage(const FieldTable& headers)
{
    boost::intrusive_ptr<Message> msg(new Message());
    AMQFrame method((MessageTransferBody(ProtocolVersion(), EMPTY, 0, 0)));
    AMQFrame header((AMQHeaderBody()));
    header.setBof(false);
    header.setEof(true);
    header.setBos(true);
    header.setEos(true);
    msg->getFrames().append(method);
    msg->getFrames().append(header);
    MessageProperties* props = msg->getFrames().getHeaders()->get<MessageProperties>(true);
    props->setApplicationHeaders(headers);
    return msg;
}

struct AppendingHandler : FrameHandler
{
    boost::intrusive_ptr<Message> msg;
    
    AppendingHandler(boost::intrusive_ptr<Message> m) : msg(m) {}

    void handle(AMQFrame& f)
    {
        msg->getFrames().append(f);
    }
};

boost::intrusive_ptr<Message> ReplicatingEventListener::cloneMessage(Queue& queue, boost::intrusive_ptr<Message> original)
{
    boost::intrusive_ptr<Message> copy(new Message());
    AMQFrame method((MessageTransferBody(ProtocolVersion(), EMPTY, 0, 0)));
    AppendingHandler handler(copy);
    handler.handle(method);
    original->sendHeader(handler, std::numeric_limits<int16_t>::max());
    original->sendContent(queue, handler, std::numeric_limits<int16_t>::max());
    return copy;
}
    
Options* ReplicatingEventListener::getOptions() 
{ 
    return &options; 
}

void ReplicatingEventListener::initialize(Plugin::Target& target)
{
      Broker* broker = dynamic_cast<broker::Broker*>(&target);
      if (broker && !options.queue.empty()) {
          if (options.createQueue) {
              queue = broker->getQueues().declare(options.queue).first;
          } else {
              queue = broker->getQueues().find(options.queue);
          }
          if (queue) {
              QueueEvents::EventListener callback = boost::bind(&ReplicatingEventListener::handle, this, _1);
              broker->getQueueEvents().registerListener(options.name, callback);
              QPID_LOG(info, "Registered replicating queue event listener");
          } else {
              QPID_LOG(error, "Replication queue named '" << options.queue << "' does not exist; replication plugin disabled.");
          }
      }
}

void ReplicatingEventListener::earlyInitialize(Target&) {}

ReplicatingEventListener::PluginOptions::PluginOptions() : Options("Queue Replication Options"), 
                                                           name("replicator"), 
                                                           createQueue(false)
{
    addOptions()
        ("replication-queue", optValue(queue, "QUEUE"), "Queue on which events for other queues are recorded")
        ("replication-listener-name", optValue(name, "NAME"), "name by which to register the replicating event listener")
        ("create-replication-queue", optValue(createQueue), "if set, the replication will be created if it does not exist");
}

static ReplicatingEventListener plugin;

}} // namespace qpid::replication
