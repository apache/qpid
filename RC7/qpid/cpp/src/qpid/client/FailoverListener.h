#ifndef QPID_CLIENT_FAILOVERLISTENER_H
#define QPID_CLIENT_FAILOVERLISTENER_H

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

#include "qpid/client/MessageListener.h"
#include "qpid/Url.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include <vector>

namespace qpid {
namespace client {

class SubscriptionManager;

/**
 * @internal Listen for failover updates from the amq.failover exchange.
 */
class FailoverListener : public MessageListener, private qpid::sys::Runnable 
{
  public:
    FailoverListener(const boost::shared_ptr<ConnectionImpl>&, const std::vector<Url>& initUrls);
    ~FailoverListener();
    void stop();

    std::vector<Url> getKnownBrokers() const;
    void received(Message& msg);
    void run();
    
  private:
    mutable sys::Mutex lock;
    std::auto_ptr<SubscriptionManager> subscriptions;
    sys::Thread thread;
    std::vector<Url> knownBrokers;
};
}} // namespace qpid::client

#endif  /*!QPID_CLIENT_FAILOVERLISTENER_H*/
