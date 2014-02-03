#ifndef QPID_HA_ERRORLISTENER_H
#define QPID_HA_ERRORLISTENER_H

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

#include "qpid/broker/SessionHandler.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace ha {

/** Default ErrorListener for HA module */
class ErrorListener : public broker::SessionHandler::ErrorListener {
  public:
    ErrorListener(const std::string& logPrefix_) : logPrefix(logPrefix_) {}

    void connectionException(framing::connection::CloseCode code, const std::string& msg) {
        QPID_LOG(error, logPrefix << framing::createConnectionException(code, msg).what());
    }
    void channelException(framing::session::DetachCode code, const std::string& msg) {
        QPID_LOG(error, logPrefix << framing::createChannelException(code, msg).what());
    }
    void executionException(framing::execution::ErrorCode code, const std::string& msg) {
        QPID_LOG(error, logPrefix << framing::createSessionException(code, msg).what());
    }
    void incomingExecutionException(framing::execution::ErrorCode code, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Incoming " << framing::createSessionException(code, msg).what());
    }
    void detach() {
        QPID_LOG(error, logPrefix << "Session detached.");
    }

  private:
    std::string logPrefix;
};


}} // namespace qpid::ha

#endif  /*!QPID_HA_ERRORLISTENER_H*/
