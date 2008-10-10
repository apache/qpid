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
#include <qpid/client/FlowControl.h>
#include <qpid/sys/Runnable.h>




namespace qpid {
namespace client {


class FailoverSubscriptionManager
{
  public:

    FailoverSubscriptionManager ( FailoverSession * fs );

    void foo ( int& arg_1 );

    void subscribe ( MessageListener   & listener,
                     const std::string & queue,
                     const FlowControl & flow,
                     const std::string & tag = std::string() );

    void subscribe ( LocalQueue        & localQueue,
                     const std::string & queue,
                     const FlowControl & flow,
                     const std::string & tag=std::string());

    void subscribe ( MessageListener   & listener,
                     const std::string & queue,
                     const std::string & tag = std::string());

    void subscribe ( LocalQueue        & localQueue,
                     const std::string & queue,
                     const std::string & tag=std::string());

    bool get ( Message & result, 
               const std::string & queue, 
               sys::Duration timeout=0);

    void cancel ( const std::string tag );

    void run ( );

    void start ( );

    void setAutoStop ( bool set = true );

    void stop ( );

    void setFlowControl ( const std::string & destintion, 
                          const FlowControl & flow );

    void setFlowControl ( const FlowControl & flow );

    const FlowControl & getFlowControl ( ) const;

    void setFlowControl ( const std::string & tag, 
                          uint32_t messages,  
                          uint32_t bytes, 
                          bool window=true );

    void setFlowControl ( uint32_t messages,  
                          uint32_t bytes, 
                          bool window = true
                        );

    void setAcceptMode ( bool required );

    void setAcquireMode ( bool acquire );

    void setAckPolicy ( const AckPolicy & autoAck );

    AckPolicy & getAckPolicy();

    void registerFailoverHandler ( boost::function<void ()> fh );

    // Get ready for a failover.
    void prepareForFailover ( Session newSession );
    void failover ( );

    std::string name;


  private:
    
    SubscriptionManager * subscriptionManager;

    MessageListener * savedListener;
    std::string       savedQueue,
                      savedTag;

    friend class FailoverConnection;
    friend class FailoverSession;

    Session newSession;
    bool    newSessionIsValid;

    /*
     * */
    typedef boost::function<void ()> subscribeFn;
    std::vector < subscribeFn > subscribeFns;

    struct subscribeArgs
    {
      int interface;
      MessageListener   * listener;
      LocalQueue        * localQueue;
      const std::string * queue;
      const FlowControl * flow;
      const std::string * tag;

      subscribeArgs ( int _interface,
                      MessageListener   *,
                      LocalQueue  *,
                      const std::string *,
                      const FlowControl *,
                      const std::string *
                    );
    };

    std::vector < subscribeArgs * > subscriptionReplayVector;

};

}} // namespace qpid::client


#endif  /*!QPID_CLIENT_FAILOVERSUBSCRIPTIONMANAGER_H*/
