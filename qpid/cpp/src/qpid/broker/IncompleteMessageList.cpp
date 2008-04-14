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
#include "IncompleteMessageList.h"

#include "Message.h"

namespace qpid {
namespace broker {

void IncompleteMessageList::add(boost::intrusive_ptr<Message> msg)
{
    incomplete.push_back(msg);
}

void IncompleteMessageList::process(CompletionListener l, bool sync)
{
    while (!incomplete.empty()) {
        boost::intrusive_ptr<Message>& msg = incomplete.front();
        if (!msg->isEnqueueComplete()) {
            if (sync){
                msg->flush();
                msg->waitForEnqueueComplete();
            } else {
                //leave the message as incomplete for now
                return;
            }            
        }
        l(msg);
        incomplete.pop_front();
    }
}

}}
