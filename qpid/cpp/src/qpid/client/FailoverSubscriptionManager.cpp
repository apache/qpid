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

#include "qpid/client/FailoverSession.h"
#include "qpid/client/FailoverSubscriptionManager.h"



using namespace std;


namespace qpid {
namespace client {



FailoverSubscriptionManager::FailoverSubscriptionManager ( FailoverSession * fs) :
    newSessionIsValid(false),
    no_failover(false)
{
    subscriptionManager = new SubscriptionManager(fs->session);
    fs->setFailoverSubscriptionManager(this);
}



void
FailoverSubscriptionManager::prepareForFailover ( Session _newSession )
{
    sys::Monitor::ScopedLock l(lock);
    newSession = _newSession;
    newSessionIsValid = true;
}



void
FailoverSubscriptionManager::failover ( )
{
    sys::Monitor::ScopedLock l(lock);
    // Stop the subscription manager thread so it can notice 
    // the failover in progress.
    subscriptionManager->stop();
    lock.notifyAll();
}




void 
FailoverSubscriptionManager::subscribe ( MessageListener   & listener,
                                         const std::string & queue,
                                         const SubscriptionSettings & settings,
                                         const std::string & tag,
                                         bool                record_this
)
{
    sys::Monitor::ScopedLock l(lock);

    subscriptionManager->subscribe ( listener,
                                     queue,
                                     settings,
                                     tag
    );
    if ( record_this )
      subscribeFns.push_back ( boost::bind ( (void (FailoverSubscriptionManager::*)(MessageListener&, const std::string&, const SubscriptionSettings&, const std::string&, bool) )  &FailoverSubscriptionManager::subscribe, this, boost::ref(listener), queue, settings, tag, false ) );
}



void 
FailoverSubscriptionManager::subscribe ( LocalQueue        & localQueue,
                                         const std::string & queue,
                                         const SubscriptionSettings & settings,
                                         const std::string & tag,
                                         bool                record_this
)
{
    sys::Monitor::ScopedLock l(lock);

    subscriptionManager->subscribe ( localQueue,
                                     queue,
                                     settings,
                                     tag
    );

    if ( record_this )
      subscribeFns.push_back ( boost::bind ( (void (FailoverSubscriptionManager::*)(LocalQueue&, const std::string&, const SubscriptionSettings&, const std::string&, bool) )  &FailoverSubscriptionManager::subscribe, this, localQueue, queue, settings, tag, false ) );
}



void
FailoverSubscriptionManager::subscribe ( MessageListener   & listener,
                                         const std::string & queue,
                                         const std::string & tag,
                                         bool                record_this
)
{
    sys::Monitor::ScopedLock l(lock);

    subscriptionManager->subscribe ( listener,
                                     queue,
                                     tag
    );
    
    if ( record_this )
      subscribeFns.push_back ( boost::bind ( (void (FailoverSubscriptionManager::*)(MessageListener&, const std::string&, const std::string&, bool) )  &FailoverSubscriptionManager::subscribe, this, boost::ref(listener), queue, tag, false ) );
}




void 
FailoverSubscriptionManager::subscribe ( LocalQueue        & localQueue,
                                         const std::string & queue,
                                         const std::string & tag,
                                         bool                record_this
)
{
    sys::Monitor::ScopedLock l(lock);

    subscriptionManager->subscribe ( localQueue,
                                     queue,
                                     tag
    );

    if ( record_this )
      subscribeFns.push_back ( boost::bind ( (void (FailoverSubscriptionManager::*)(LocalQueue&, const std::string&, const std::string&, bool) ) &FailoverSubscriptionManager::subscribe, this, localQueue, queue, tag, false ) );
}



bool 
FailoverSubscriptionManager::get ( Message & result,
                                   const std::string & queue,
                                   sys::Duration timeout
)
{

    return subscriptionManager->get ( result, queue, timeout );
}



void 
FailoverSubscriptionManager::cancel ( const std::string tag )
{

    subscriptionManager->cancel ( tag );
}



void 
FailoverSubscriptionManager::run ( ) // User Thread
{
    std::vector<subscribeFn> mySubscribeFns;

    while ( 1 )
    {
        subscriptionManager->run ( );

        // When we drop out of run, if there is a new Session
        // waiting for us, this is a failover.  Otherwise, just
        // return control to usercode.
        
        {
          sys::Monitor::ScopedLock l(lock);


          while ( !newSessionIsValid && !no_failover )
            lock.wait();


          if ( newSessionIsValid ) 
          {
             newSessionIsValid = false;
             delete subscriptionManager;
             subscriptionManager = new SubscriptionManager(newSession);
             mySubscribeFns.swap ( subscribeFns );
          }
          else
          {
              // Not a failover, return to user code.
              break;
          }
        }

        for ( std::vector<subscribeFn>::iterator i = mySubscribeFns.begin(); 
              i != mySubscribeFns.end(); 
              ++ i 
        )
        {
            (*i) ();
        }

    }
}


void 
FailoverSubscriptionManager::start ( )
{

    subscriptionManager->start ( );
}



void 
FailoverSubscriptionManager::setAutoStop ( bool set )
{

    subscriptionManager->setAutoStop ( set );
}



void 
FailoverSubscriptionManager::stop ( )
{
    sys::Monitor::ScopedLock l(lock);

    no_failover = true;
    subscriptionManager->stop ( );
    lock.notifyAll();
}

}} // namespace qpid::client
