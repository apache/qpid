#ifndef QPID_CLIENT_FAILOVERSUBSCRIPTIONMANAGER_H
#define QPID_CLIENT_FAILOVERSUBSCRIPTIONMANAGER_H

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
#include <qpid/client/Dispatcher.h>
#include <qpid/client/Completion.h>
#include <qpid/client/Session.h>
#include <qpid/client/FailoverSession.h>
#include <qpid/client/MessageListener.h>
#include <qpid/client/SubscriptionManager.h>
#include <qpid/client/LocalQueue.h>
#include <qpid/client/SubscriptionSettings.h>
#include <qpid/sys/Runnable.h>
#include <qpid/sys/Monitor.h>




namespace qpid {
namespace client {


class FailoverSubscriptionManager
{
  public:

    FailoverSubscriptionManager ( FailoverSession * fs );

    void subscribe ( MessageListener   & listener,
                     const std::string & queue,
                     const SubscriptionSettings & ,
                     const std::string & tag = std::string(),
                     bool  record_this = true );

    void subscribe ( LocalQueue        & localQueue,
                     const std::string & queue,
                     const SubscriptionSettings & ,
                     const std::string & tag=std::string(),
                     bool  record_this = true );

    void subscribe ( MessageListener   & listener,
                     const std::string & queue,
                     const std::string & tag = std::string(),
                     bool  record_this = true );

    void subscribe ( LocalQueue        & localQueue,
                     const std::string & queue,
                     const std::string & tag=std::string(),
                     bool  record_this = true );

    bool get ( Message & result, 
               const std::string & queue, 
               sys::Duration timeout=0);

    void cancel ( const std::string tag );

    void run ( );

    void start ( );

    void setAutoStop ( bool set = true );

    void stop ( );

    // Get ready for a failover.
    void prepareForFailover ( Session newSession );
    void failover ( );


  private:
    sys::Monitor lock;
    
    SubscriptionManager * subscriptionManager;

    MessageListener * savedListener;
    std::string       savedQueue,
                      savedTag;

    friend class FailoverConnection;
    friend class FailoverSession;

    Session newSession;
    bool    newSessionIsValid;
    bool    no_failover;


    typedef boost::function<void ()> subscribeFn;
    std::vector < subscribeFn > subscribeFns;
};

}} // namespace qpid::client


#endif  /*!QPID_CLIENT_FAILOVERSUBSCRIPTIONMANAGER_H*/
