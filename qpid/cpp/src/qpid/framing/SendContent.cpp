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

#include "SendContent.h"

qpid::framing::SendContent::SendContent(FrameHandler& h, uint16_t c, uint16_t mfs) : handler(h), channel(c), maxFrameSize(mfs) {}

void qpid::framing::SendContent::operator()(AMQFrame& f) const
{
    uint16_t maxContentSize = maxFrameSize - AMQFrame::frameOverhead();
    const AMQContentBody* body(f.castBody<AMQContentBody>()); 
    if (body->size() > maxContentSize) {
        uint32_t offset = 0;
        for (int chunk = body->size() / maxContentSize; chunk > 0; chunk--) {
            sendFragment(*body, offset, maxContentSize);
            offset += maxContentSize;
        }
        uint32_t remainder = body->size() % maxContentSize;
        if (remainder) {
            sendFragment(*body, offset, remainder);
        }
    } else {
        AMQFrame copy(f);
        copy.setChannel(channel);
        handler.handle(copy);
    }        
}

void qpid::framing::SendContent::sendFragment(const AMQContentBody& body, uint32_t offset, uint16_t size) const
{
    AMQFrame fragment(channel, AMQContentBody(body.getData().substr(offset, size)));
    handler.handle(fragment);
}
