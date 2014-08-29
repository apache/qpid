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

#include "AsyncCommandCallback.h"
#include "SessionOutputException.h"


namespace qpid {
namespace broker {

using namespace framing;

AsyncCommandCallback::AsyncCommandCallback(SessionState& ss, Command f, bool sync) :
    AsyncCommandContext(ss), command(f), channel(ss.getChannel()), syncPoint(sync)
{}

void AsyncCommandCallback::completed(bool sync) {
    if (sync)
        doCommand(); // In initiating thread, execute now.
    else
        completerContext->schedule(
            boost::bind(&AsyncCommandCallback::complete,
                        boost::intrusive_ptr<AsyncCommandCallback>(this)));
}

boost::intrusive_ptr<AsyncCompletion::Callback> AsyncCommandCallback::clone() {
    return new AsyncCommandCallback(*this);
}

void AsyncCommandCallback::complete() {
    try{
        doCommand();
    } catch (const SessionException& e) {
        throw SessionOutputException(e, channel);
    } catch (const std::exception& e) {
        throw SessionOutputException(InternalErrorException(e.what()), channel);
    }
}

void AsyncCommandCallback::doCommand() {
    SessionState* session = completerContext->getSession();
    if (session && session->isAttached()) {
        std::string result = command(); // Execute the command now.
        // Send completion now unless this is a syncPoint and there are incomplete commands.
        if (!(syncPoint && session->addPendingExecutionSync(id)))
            session->completeCommand(id, false, requiresSync, result);
    }
    else
        throw InternalErrorException("Cannot complete command, no session");
}

}} // namespace qpid::broker
