#ifndef _tests_MockChannel_h
#define _tests_MockChannel_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/framing/ChannelAdapter.h"
#include "qpid/framing/OutputHandler.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/BasicGetBody.h"
#include <boost/shared_ptr.hpp>
#include <vector>

/** Mock output handler to collect frames */
struct MockOutputHandler : public qpid::framing::OutputHandler {
    std::vector<qpid::framing::AMQFrame> frames;
    void send(qpid::framing::AMQFrame& frame){ frames.push_back(frame); }
};

/**
 * Combination mock OutputHandler and ChannelAdapter for tests.
 */
struct MockChannel : public qpid::framing::ChannelAdapter
{
    MockOutputHandler out;

    MockChannel(qpid::framing::ChannelId id) {
        init(id, out, qpid::framing::ProtocolVersion());
    }

    bool isOpen() const { return true; }

    void handleHeader(qpid::framing::AMQHeaderBody* b) { send(*b); }
    void handleContent(qpid::framing::AMQContentBody* b) { send(*b); }
    void handleHeartbeat(qpid::framing::AMQHeartbeatBody* b) { send(*b); }
    void handleMethod(qpid::framing::AMQMethodBody* b) { send(*b); };

};

#endif  /*!_tests_MockChannel_h*/
