#ifndef QPID_CLIENT_FAILOVERMANAGER_H
#define QPID_CLIENT_FAILOVERMANAGER_H

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

#include "Connection.h"
#include "ConnectionSettings.h"
#include "qpid/Exception.h"
#include "qpid/client/AsyncSession.h"
#include "qpid/sys/Monitor.h"
#include <vector>

namespace qpid {
namespace client {

struct CannotConnectException : qpid::Exception 
{
    CannotConnectException(const std::string& m) : qpid::Exception(m) {}
};

/**
 * Utility to handle reconnection.
 */
class FailoverManager
{
  public:
    struct Command
    {
        virtual void execute(AsyncSession& session, bool isRetry) = 0;
        virtual ~Command() {}
    };

    FailoverManager(const ConnectionSettings& settings);
    Connection& connect(std::vector<Url> brokers = std::vector<Url>());
    Connection& getConnection();
    void close();
    void execute(Command&);
  private:
    enum State {IDLE, CONNECTING, CANT_CONNECT};

    qpid::sys::Monitor lock;
    Connection connection;
    ConnectionSettings settings;
    State state;

    void attempt(Connection&, ConnectionSettings settings, std::vector<Url> urls);
    void attempt(Connection&, ConnectionSettings settings);
};
}} // namespace qpid::client

#endif  /*!QPID_CLIENT_FAILOVERMANAGER_H*/
