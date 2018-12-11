#ifndef QPID_CLUSTER_EXP_MESSAGEBUILDERS_H
#define QPID_CLUSTER_EXP_MESSAGEBUILDERS_H

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

#include "qpid/cluster/types.h"
#include "qpid/broker/MessageBuilder.h"
#include <map>

namespace qpid {
namespace broker {
class Queue;
}
namespace framing {
class AMQFrame;
}

namespace cluster {

/**
 * Build messages received by CPG delivery.
 */
class MessageBuilders
{
  public:
    MessageBuilders(broker::MessageStore* store);

    /** Announce a message for queue arriving on channel from sender. */
    void announce(MemberId sender, uint16_t channel,
                  const boost::shared_ptr<broker::Queue>&);

    /** Add a frame to the message in progress.
     *@param sender member that sent the frame.
     *@param frame is the frame to add.
     *@param queueOut set to the queue if message complete.
     *@param messageOut set to message if message complete.
     *@return True if the frame completes a message
     */
    bool handle(MemberId sender, const framing::AMQFrame& frame,
                boost::shared_ptr<broker::Queue>& queueOut,
                boost::intrusive_ptr<broker::Message>& messageOut);

  private:
    typedef std::pair<MemberId, uint16_t> ChannelId;
    typedef std::pair<boost::shared_ptr<broker::Queue>,
                      boost::shared_ptr<broker::MessageBuilder> > QueueBuilder;
    typedef std::map<ChannelId, QueueBuilder> Map;
    Map map;
    broker::MessageStore* store;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_MESSAGEBUILDERS_H*/
