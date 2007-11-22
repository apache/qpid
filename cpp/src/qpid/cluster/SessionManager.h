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

#include "ClassifierHandler.h"

#include "qpid/framing/HandlerUpdater.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/sys/Mutex.h"

#include <boost/noncopyable.hpp>
#include <boost/scoped_ptr.hpp>
#include <boost/optional.hpp>

#include <map>

namespace qpid {

namespace broker {
class Broker;
}

namespace framing {
class Uuid;
}

namespace cluster {

/**
 * Manage the clusters session map.
 * 
 */
class SessionManager : public framing::HandlerUpdater, public framing::FrameHandler,
                       private boost::noncopyable
{
  public:
    SessionManager(broker::Broker& broker, framing::FrameHandler& cluster);
    ~SessionManager();

    /** ChannelUpdater: add cluster handlers to session. */
    void update(framing::ChannelId, framing::FrameHandler::Chains&);

    /** FrameHandler: map frames from the cluster to sessions. */
    void handle(framing::AMQFrame&);

    /** Get ChannelID for UUID. Return 0 if no mapping */
    framing::ChannelId getChannelId(const framing::Uuid&) const;
    
  private:
    class SessionOperations;
    class BrokerHandler;

    struct Session {
        Session(framing::FrameHandler& cluster, framing::FrameHandler& cont_)
            : cont(cont_), classifier(cluster,cont_) {}
        framing::FrameHandler& cont; // Continue local dispatch
        ClassifierHandler classifier;
    };

    typedef std::map<framing::ChannelId,boost::optional<Session> > SessionMap;

    sys::Mutex lock;
    framing::FrameHandler& cluster;
    boost::scoped_ptr<BrokerHandler> localBroker;
    SessionMap sessions;
};


}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CHANNELMANAGER_H*/
