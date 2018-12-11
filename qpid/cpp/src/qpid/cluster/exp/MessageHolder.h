#ifndef QPID_CLUSTER_EXP_MESSAGEHOLDER_H
#define QPID_CLUSTER_EXP_MESSAGEHOLDER_H

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

#include "qpid/sys/Mutex.h"
#include "qpid/framing/AMQFrame.h"
#include <boost/intrusive_ptr.hpp>
#include <map>

namespace qpid {

namespace broker {
class Message;
class Queue;
}

namespace cluster {

/**
 * Holds locally-received messages until the corresponding transfer
 * multicast is self delivered.
 * THREAD SAFE: updated in broker and deliver threads.
 */
class MessageHolder
{
  public:
    typedef uint16_t Channel;
    typedef boost::intrusive_ptr<broker::Message> MessagePtr;
    typedef boost::shared_ptr<broker::Queue> QueuePtr;

    MessageHolder() : mark(0) {}

    /**
     * Called in broker thread by BrokerContext just before a local message is enqueud.
     *@return channel to use to send the message.
     */
    uint16_t sending(const MessagePtr& msg, const QueuePtr &q);

    /** Called in deliver thread by MessageHandler as frames are received.
     * If this is the last frame of the message, return the corresponding local message
     *@return true if this is the last frame.
     */
    bool check(const framing::AMQFrame& frame, QueuePtr& queueOut, MessagePtr& msgOut);

  private:
    typedef std::pair<MessagePtr, QueuePtr> MessageAndQueue;
    typedef std::map<Channel, MessageAndQueue> MessageMap;

    Channel getChannel(const sys::Mutex::ScopedLock&);

    sys::Mutex lock;
    MessageMap messages;
    Channel mark;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_MESSAGEHOLDER_H*/
