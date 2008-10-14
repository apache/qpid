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



#include "qpid/log/Statement.h"
#include "qpid/client/FailoverConnection.h"
#include "qpid/client/ConnectionSettings.h"

#include <iostream>
#include <fstream>

using namespace std;


namespace qpid {
namespace client {


FailoverConnection::FailoverConnection ( ) :
    name(),
    failoverCompleteTime(0)
{
    connection.registerFailureCallback
        ( boost::bind(&FailoverConnection::failover, this));
}

FailoverConnection::~FailoverConnection () {}

void 
FailoverConnection::open ( const std::string& host,
                           int   port,
                           const std::string& uid,
                           const std::string& pwd,
                           const std::string& virtualhost, 
                           uint16_t maxFrameSize
)
{
    ConnectionSettings settings;

    settings.host         = host;
    settings.port         = port;
    settings.username     = uid;
    settings.username     = uid;
    settings.password     = pwd;
    settings.virtualhost  = virtualhost;
    settings.maxFrameSize = maxFrameSize;
    settings.host         = host;

    open ( settings );
}


void
FailoverConnection::open ( ConnectionSettings & settings )
{
    connection.open ( settings );
}



void 
FailoverConnection::close ( )
{
    connection.close();
}



FailoverSession *
FailoverConnection::newSession ( const std::string& /* name */ )
{
    FailoverSession * fs = new FailoverSession;
    sessions.push_back ( fs );
    fs->session = connection.newSession();
    return fs;
}



void
FailoverConnection::resume ( FailoverSession & failoverSession )
{
    connection.resume ( failoverSession.session );
}


bool 
FailoverConnection::isOpen() const
{
    return connection.isOpen();
}


void 
FailoverConnection::getKnownBrokers ( std::vector<std::string> & /*v*/ )
{
}


void 
FailoverConnection::registerFailureCallback ( boost::function<void ()> /*fn*/ )
{
}

void 
FailoverConnection::failover ( )
{
    std::vector<Url> knownBrokers = connection.getKnownBrokers();
    if (knownBrokers.empty())
        throw Exception(QPID_MSG("FailoverConnection::failover " << name << " no known brokers."));

    Connection newConnection;
    for (std::vector<Url>::iterator i = knownBrokers.begin(); i != knownBrokers.end(); ++i) {
        try {
            newConnection.open(*i);
            break;
        }
        catch (const std::exception& e) {
            QPID_LOG(info, "Could not fail-over to " << *i << ": " << e.what());
            if ((i + 1) == knownBrokers.end())
                throw;
        }
    }
  
    /*
     * We have a valid new connection.  Tell all the sessions
     * (and, through them, their SessionManagers and whatever else)
     * that we are about to failover to this new Connection.
     */

    // FIXME aconway 2008-10-10: thread unsafe, possible race with concurrent newSession 
    std::vector<FailoverSession *>::iterator sessions_iterator;
    for ( sessions_iterator = sessions.begin();
          sessions_iterator < sessions.end();
          ++ sessions_iterator
    )
    {
        FailoverSession * fs = * sessions_iterator;
        fs->prepareForFailover ( newConnection );
    }

    connection = newConnection;
    connection.registerFailureCallback
        ( boost::bind(&FailoverConnection::failover, this));

    /*
     * Tell all sessions to actually failover to the new connection.
     */
    for ( sessions_iterator = sessions.begin();
          sessions_iterator < sessions.end();
          ++ sessions_iterator
    )
    {
        FailoverSession * fs = * sessions_iterator;
        fs->failover ( );
    }
}




}} // namespace qpid::client
