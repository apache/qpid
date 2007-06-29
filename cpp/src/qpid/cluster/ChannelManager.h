#ifndef QPID_CLUSTER_CHANNELMANAGER_H
#define QPID_CLUSTER_CHANNELMANAGER_H

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/framing/HandlerUpdater.h"
#include <map>

namespace qpid {
namespace cluster {

/**
 * Manage channels and handler chains on channels for the cluster.
 *
 * As HandlerUpdater plugin, updates channel handler chains with
 * cluster handlers.
 *
 * As a FrameHandler handles frames coming from the cluster and
 * dispatches them to the appropriate channel handler.
 * 
 */
class ChannelManager : public framing::HandlerUpdater,
                       public framing::FrameHandler
{
  public:
    /** Takes a handler to send frames to the cluster. */
    ChannelManager(framing::FrameHandler::Chain mcastOut);
    
    /** As FrameHandler handle frames from the cluster */
    void handle(framing::AMQFrame& frame);

    /** As ChannelUpdater update the handler chains. */
    void update(framing::ChannelId id, framing::FrameHandler::Chains& chains);
    
  private:
    void updateFailoverState(framing::AMQFrame&);
    
    typedef std::map<framing::ChannelId,
                    framing::FrameHandler::Chains> ChannelMap;

    framing::FrameHandler::Chain mcastOut;
    ChannelMap channels;
};


}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CHANNELMANAGER_H*/
