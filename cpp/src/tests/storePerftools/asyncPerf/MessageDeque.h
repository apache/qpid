/*
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
 */

/**
 * \file MessageDeque.h
 */

/*
 * This is a copy of qpid::broker::MessageDeque.h, but using the local
 * tests::storePerftools::asyncPerf::QueuedMessage class instead of
 * qpid::broker::QueuedMessage.
 */

#ifndef tests_storePerftools_asyncPerf_MessageDeque_h_
#define tests_storePerftools_asyncPerf_MessageDeque_h_

#include "Messages.h"

#include "qpid/sys/Mutex.h"

#include <deque>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class MessageDeque : public Messages
{
public:
    MessageDeque();
    virtual ~MessageDeque();
    uint32_t size();
    bool push(const QueuedMessage& added, QueuedMessage& removed);
    bool consume(QueuedMessage& msg);
private:
    std::deque<QueuedMessage> m_messages;
    qpid::sys::Mutex m_msgMutex;

};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_MessageDeque_h_
