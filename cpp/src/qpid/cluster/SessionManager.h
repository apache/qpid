#ifndef QPID_CLUSTER_SESSIONMANAGER_H
#define QPID_CLUSTER_SESSIONMANAGER_H

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

#include "qpid/cluster/SessionFrame.h"
#include "qpid/framing/HandlerUpdater.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/Mutex.h"

#include <boost/noncopyable.hpp>

#include <map>

namespace qpid {

namespace broker {
class Broker;
}

namespace cluster {

/**
 * Manage sessions and handler chains for the cluster.
 * 
 */
class SessionManager : public framing::HandlerUpdater, public SessionFrameHandler,
                       private boost::noncopyable
{
  public:
    SessionManager(broker::Broker& broker);

    /** Set the handler to send to the cluster */
    void setClusterSend(const SessionFrameHandler::Chain& send) { clusterSend=send; }
    
    /** As ChannelUpdater update the handler chains. */
    void update(framing::FrameHandler::Chains& chains);

    /** As SessionFrameHandler handle frames received from the cluster */
    void handle(SessionFrame&);

    /** Get ChannelID for UUID. Return 0 if no mapping */
    framing::ChannelId getChannelId(const framing::Uuid&) const;
    
  private:
    class SessionOperations;
    typedef std::map<framing::Uuid,framing::FrameHandler::Chains> SessionMap;

    sys::Mutex lock;
    SessionFrameHandler::Chain clusterSend;
    framing::FrameHandler::Chain localBroker;
    SessionMap sessions;
};


}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CHANNELMANAGER_H*/
