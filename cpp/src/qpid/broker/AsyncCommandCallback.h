#ifndef QPID_BROKER_ASYNCCOMMANDCALLBACK_H
#define QPID_BROKER_ASYNCCOMMANDCALLBACK_H

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

#include "qpid/broker/SessionState.h"
#include "qpid/broker/AsyncCompletion.h"

namespace qpid {
namespace broker {

/**
 * An AsyncCompletion::Callback that executes the final part of an
 * async-completed command in the proper context:
 *
 * - Complete synchronously: Called in the initiating thread.
 * - Complete asynchronously: Scheduled on the IO thread.
 *
 * Errors thrown by the command are returned to the correct session on the client
 * even if we are executed via an IO callback.
 */
class AsyncCommandCallback : public SessionState::AsyncCommandContext {
  public:
    /** Command function returns a string containing the encoded result of the
     * command, or empty for no result.  It may raise an exception.
     */
    typedef boost::function<std::string ()> Command;

    /**
     * @param syncPoint: if true have this command complete only when all
     * preceeding commands are complete, like execution.sync.
     */
    AsyncCommandCallback(SessionState& ss, Command f, bool syncPoint=false);

    void completed(bool sync);

    boost::intrusive_ptr<AsyncCompletion::Callback> clone();

  private:
    void complete();
    void doCommand();

    Command command;
    uint16_t channel;
    bool syncPoint;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_ASYNCCOMMANDCALLBACK_H*/
