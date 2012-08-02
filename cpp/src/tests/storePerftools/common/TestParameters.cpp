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
 * \file TestParameters.cpp
 */

#include "TestParameters.h"

#include <cstdlib> // std::atoi, std::atol

namespace tests {
namespace storePerftools {
namespace common {

// static declarations
uint32_t TestParameters::s_defaultNumMsgs = 1024;
uint32_t TestParameters::s_defaultMsgSize = 1024;
uint16_t TestParameters::s_defaultNumQueues = 1;
uint16_t TestParameters::s_defaultEnqThreadsPerQueue = 1;
uint16_t TestParameters::s_defaultDeqThreadsPerQueue = 1;

TestParameters::TestParameters():
        Parameters(),
        m_numMsgs(s_defaultNumMsgs),
        m_msgSize(s_defaultMsgSize),
        m_numQueues(s_defaultNumQueues),
        m_numEnqThreadsPerQueue(s_defaultEnqThreadsPerQueue),
        m_numDeqThreadsPerQueue(s_defaultDeqThreadsPerQueue)//,
{}

TestParameters::TestParameters(const uint32_t numMsgs,
                               const uint32_t msgSize,
                               const uint16_t numQueues,
                               const uint16_t numEnqThreadsPerQueue,
                               const uint16_t numDeqThreadsPerQueue) :
        Parameters(),
        m_numMsgs(numMsgs),
        m_msgSize(msgSize),
        m_numQueues(numQueues),
        m_numEnqThreadsPerQueue(numEnqThreadsPerQueue),
        m_numDeqThreadsPerQueue(numDeqThreadsPerQueue)
{}

TestParameters::TestParameters(const TestParameters& tp):
        Parameters(),
        m_numMsgs(tp.m_numMsgs),
        m_msgSize(tp.m_msgSize),
        m_numQueues(tp.m_numQueues),
        m_numEnqThreadsPerQueue(tp.m_numEnqThreadsPerQueue),
        m_numDeqThreadsPerQueue(tp.m_numDeqThreadsPerQueue)
{}

TestParameters::~TestParameters() {}

void
TestParameters::toStream(std::ostream& os) const {
    os << "Test Parameters:" << std::endl;
    os << "  num_msgs = " << m_numMsgs << std::endl;
    os << "  msg_size = " << m_msgSize << std::endl;
    os << "  num_queues = " << m_numQueues << std::endl;
    os << "  num_enq_threads_per_queue = " << m_numEnqThreadsPerQueue << std::endl;
    os << "  num_deq_threads_per_queue = " << m_numDeqThreadsPerQueue << std::endl;
}

bool
TestParameters::parseArg(const int arg,
                         const char* optarg) {
    switch(arg) {
    case 'm':
        m_numMsgs = uint32_t(std::atol(optarg));
        break;
    case 'S':
        m_msgSize = uint32_t(std::atol(optarg));
        break;
    case 'q':
        m_numQueues = uint16_t(std::atoi(optarg));
        break;
    case 'e':
        m_numEnqThreadsPerQueue = uint16_t(std::atoi(optarg));
        break;
    case 'd':
        m_numDeqThreadsPerQueue = uint16_t(std::atoi(optarg));
        break;
    default:
        return false;
    }
    return true;
}

// static
void
TestParameters::printArgs(std::ostream& os) {
    os << "Test parameters:" << std::endl;
    os << " -m --num_msgs:                   Number of messages to send per enqueue thread ["
       << TestParameters::s_defaultNumMsgs << "]" << std::endl;
    os << " -S --msg_size:                   Size of each message to be sent ["
       << TestParameters::s_defaultMsgSize << "]" << std::endl;
    os << " -q --num_queues:                 Number of simultaneous queues ["
       << TestParameters::s_defaultNumQueues << "]" << std::endl;
    os << " -e --num_enq_threads_per_queue:  Number of enqueue threads per queue ["
       << TestParameters::s_defaultEnqThreadsPerQueue << "]" << std::endl;
    os << " -d --num_deq_threads_per_queue:  Number of dequeue threads per queue ["
       << TestParameters::s_defaultDeqThreadsPerQueue << "]" << std::endl;
    os << std::endl;
}

// static
std::string
TestParameters::shortArgs() {
    return "m:S:q:e:d:";
}

}}} // namespace tests::storePerftools::common
