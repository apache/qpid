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
#include "CompletionTracker.h"

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::framing::SequenceNumber;

void CompletionTracker::track(SequenceNumber command, void* token)
{
    tokens[command] = token;
}

void CompletionTracker::completedTo(SequenceNumber command)
{
    Tokens::iterator i = tokens.lower_bound(command);
    if (i != tokens.end()) {
        lastCompleted = i->second;
        tokens.erase(tokens.begin(), ++i);
    }
}

void* CompletionTracker::getLastCompletedToken()
{
    return lastCompleted;
}

}}} // namespace qpid::client::amqp0_10
