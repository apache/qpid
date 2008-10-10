#ifndef QPID_CLIENT_FAILOVERCONNECTION_H
#define QPID_CLIENT_FAILOVERCONNECTION_H

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


#include <string>

#include "qpid/client/Connection.h"
#include "qpid/client/FailoverConnection.h"
#include "qpid/client/FailoverSession.h"
#include "qpid/client/FailoverSubscriptionManager.h"



namespace qpid {
namespace client {

class ConnectionSettings;


class FailoverConnection
{
  public:

    FailoverConnection ( );

    ~FailoverConnection ( );

    void open ( const std::string& host,
                int   port,
                const std::string& uid = "guest",
                const std::string& pwd = "guest",
                const std::string& virtualhost = "/", 
                uint16_t maxFrameSize=65535
    );

    void open ( ConnectionSettings & settings );

    void close ( );

    FailoverSession * newSession ( const std::string& name = std::string() );

    void resume ( FailoverSession & session );

    bool isOpen() const;

    void getKnownBrokers ( std::vector<std::string> & v );


    // public interface specific to Failover:

    void registerFailureCallback ( boost::function<void ()> fn );

    // If you have more than 1 connection and you want to give them 
    // separate names for debugging...
    std::string name; 

    void failover ( );

    struct timeval * failoverCompleteTime;


  private:

    std::string host;

    Connection connection;

    int currentPortNumber;

    boost::function<void ()> clientFailoverCallback;

    std::vector<FailoverSession *> sessions;


  friend class FailoverSession;
  friend class FailoverSessionManager;
};

}} // namespace qpid::client


#endif  /*!QPID_CLIENT_FAILOVERCONNECTION_H*/
