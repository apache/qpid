#ifndef QPID_CLIENT_MESSAGEREPLAYTRACKER_H
#define QPID_CLIENT_MESSAGEREPLAYTRACKER_H

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
#include "AsyncSession.h"
#include "Message.h"

#include <list>
#include <string>

namespace qpid {
namespace client {

/**
 * Utility to track messages sent asynchronously, allowing those that
 * are indoubt to be replayed over a new session.
 */
class MessageReplayTracker
{
  public:
    MessageReplayTracker(uint flushInterval);
    void send(const Message& message, const std::string& destination = "");
    void init(AsyncSession session);
    void replay(AsyncSession session);
    void setFlushInterval(uint interval);
    uint getFlushInterval();
    void checkCompletion();

    template <class F> void foreach(F& f) {
        for (std::list<ReplayRecord>::const_iterator i = buffer.begin(); i != buffer.end(); i++) {
            f(i->message);
        }
    }

  private:
    struct ReplayRecord
    {
        Completion status;
        Message message;
        std::string destination;

        ReplayRecord(const Message& message, const std::string& destination);
        void send(MessageReplayTracker&);
        bool isComplete();
    };

    AsyncSession session;
    uint flushInterval;
    uint count;
    std::list<ReplayRecord> buffer;
};
}} // namespace qpid::client

#endif  /*!QPID_CLIENT_MESSAGEREPLAYTRACKER_H*/
