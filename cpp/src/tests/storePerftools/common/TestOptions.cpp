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
 * \file TestOptions.cpp
 */

#include "TestOptions.h"

namespace tests {
namespace storePerftools {
namespace common {

// static declarations
uint32_t TestOptions::s_defaultNumMsgs = 1024;
uint32_t TestOptions::s_defaultMsgSize = 1024;
uint16_t TestOptions::s_defaultNumQueues = 1;
uint16_t TestOptions::s_defaultEnqThreadsPerQueue = 1;
uint16_t TestOptions::s_defaultDeqThreadsPerQueue = 1;

TestOptions::TestOptions(const std::string& name) :
        qpid::Options(name),
        m_numMsgs(s_defaultNumMsgs),
        m_msgSize(s_defaultMsgSize),
        m_numQueues(s_defaultNumQueues),
        m_numEnqThreadsPerQueue(s_defaultEnqThreadsPerQueue),
        m_numDeqThreadsPerQueue(s_defaultDeqThreadsPerQueue)
{
    doAddOptions();
}

TestOptions::TestOptions(const uint32_t numMsgs,
                         const uint32_t msgSize,
                         const uint16_t numQueues,
                         const uint16_t numEnqThreadsPerQueue,
                         const uint16_t numDeqThreadsPerQueue,
                         const std::string& name) :
        qpid::Options(name),
        m_numMsgs(numMsgs),
        m_msgSize(msgSize),
        m_numQueues(numQueues),
        m_numEnqThreadsPerQueue(numEnqThreadsPerQueue),
        m_numDeqThreadsPerQueue(numDeqThreadsPerQueue)
{
    doAddOptions();
}

TestOptions::~TestOptions()
{}

void
TestOptions::printVals(std::ostream& os) const
{
    os << "TEST OPTIONS:" << std::endl;
    os << "                       Number of queues [-q, --num-queues]: " << m_numQueues << std::endl;
    os << "       Number of producers per queue [-p, --num-producers]: " << m_numEnqThreadsPerQueue << std::endl;
    os << "       Number of consumers per queue [-c, --num-consumers]: " << m_numDeqThreadsPerQueue << std::endl;
    os << "  Number of messages to send per producer [-m, --num-msgs]: " << m_numMsgs << std::endl;
    os << "             Size of each message (bytes) [-s, --msg-size]: " << m_msgSize << std::endl;
}

void
TestOptions::validate()
{
    if (((m_numEnqThreadsPerQueue * m_numMsgs) % m_numDeqThreadsPerQueue) != 0) {
        throw qpid::Exception("Parameter Error: (num-producers * num-msgs) must be a multiple of num-consumers.");
    }
}

// private
void
TestOptions::doAddOptions()
{
    addOptions()
            ("num-queues,q", qpid::optValue(m_numQueues, "N"),
                    "Number of queues")
            ("num-producers,p", qpid::optValue(m_numEnqThreadsPerQueue, "N"),
                    "Number of producers per queue")
            ("num-consumers,c", qpid::optValue(m_numDeqThreadsPerQueue, "N"),
                    "Number of consumers per queue")
            ("num-msgs,m", qpid::optValue(m_numMsgs, "N"),
                    "Number of messages to send per producer")
            ("msg-size,s", qpid::optValue(m_msgSize, "N"),
                    "Size of each message (bytes)")
    ;
}

}}} // namespace tests::storePerftools::common
