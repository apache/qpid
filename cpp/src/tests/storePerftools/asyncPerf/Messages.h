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
 * \file Messages.h
 */

/*
 * This is a copy of qpid::broker::Messages.h, but using the local
 * tests::storePerftools::asyncPerf::QueuedMessage class instead of
 * qpid::broker::QueuedMessage.
 */

#ifndef tests_storePerftools_asyncPerf_Messages_h_
#define tests_storePerftools_asyncPerf_Messages_h_

#include <stdint.h>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class QueuedMessage;

class Messages
{
public:
    virtual ~Messages() {}
    virtual uint32_t size() = 0;
    virtual bool push(const QueuedMessage& added, QueuedMessage& removed) = 0;
    virtual bool consume(QueuedMessage& msg) = 0;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_Messages_h_
