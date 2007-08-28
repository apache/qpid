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
#include <string>
#include <ostream>
#include <iostream>
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/Buffer.h"

#ifndef _frame_functors_
#define _frame_functors_

namespace qpid {
namespace framing {

class SumFrameSize
{
    uint64_t size;
public:
    SumFrameSize() : size(0) {}
    void operator()(const AMQFrame& f) { size += f.size(); }
    uint64_t getSize() { return size; }
};

class SumBodySize
{
    uint64_t size;
public:
    SumBodySize() : size(0) {}
    void operator()(const AMQFrame& f) { size += f.getBody()->size(); }
    uint64_t getSize() { return size; }
};

class EncodeFrame
{
    Buffer& buffer;
public:
    EncodeFrame(Buffer& b) : buffer(b) {}
    void operator()(const AMQFrame& f) { f.encode(buffer); }
};

class EncodeBody
{
    Buffer& buffer;
public:
    EncodeBody(Buffer& b) : buffer(b) {}
    void operator()(const AMQFrame& f) { f.getBody()->encode(buffer); }
};

class AccumulateContent
{
    std::string& content;
public:
    AccumulateContent(std::string& c) : content(c) {}
    void operator()(const AMQFrame& f) { content += f.castBody<AMQContentBody>()->getData(); }
};

class Relay
{
    FrameHandler& handler;
    const uint16_t channel;

public:
    Relay(FrameHandler& h, uint16_t c) : handler(h), channel(c) {}

    void operator()(AMQFrame& f)
    {
        AMQFrame copy(f);
        copy.setChannel(channel);
        handler.handle(copy);
    }
};

class Print
{
    std::ostream& out;
public:
    Print(std::ostream& o) : out(o) {}

    void operator()(const AMQFrame& f)
    {
        out << f << std::endl;
    }
};

}
}


#endif
