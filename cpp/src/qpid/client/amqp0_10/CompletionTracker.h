#ifndef QPID_CLIENT_AMQP0_10_COMPLETIONTRACKER_H
#define QPID_CLIENT_AMQP0_10_COMPLETIONTRACKER_H

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

#include "qpid/framing/SequenceNumber.h"
#include <map>

namespace qpid {
namespace client {
namespace amqp0_10 {

/**
 * Provides a mapping from command ids to application supplied
 * 'tokens', and is used to determine when the sending or
 * acknowledging of a specific message is complete.
 */
class CompletionTracker
{
  public:
    void track(qpid::framing::SequenceNumber command, void* token);
    void completedTo(qpid::framing::SequenceNumber command);
    void* getLastCompletedToken();
  private:
    typedef std::map<qpid::framing::SequenceNumber, void*> Tokens;
    Tokens tokens;
    void* lastCompleted;
};
}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_COMPLETIONTRACKER_H*/
