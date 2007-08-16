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
#include "InMemoryContent.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ChannelAdapter.h"

using namespace qpid::broker;
using namespace qpid::framing;
using boost::static_pointer_cast;

void InMemoryContent::add(AMQContentBody* data)
{
    content.push_back(*data);
}

uint32_t InMemoryContent::size()
{
    int sum(0);
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        sum += i->size();
    }
    return sum;
}

void InMemoryContent::send(ChannelAdapter& channel, uint32_t framesize)
{
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        if (i->size() > framesize) {
            uint32_t offset = 0;
            for (int chunk = i->size() / framesize; chunk > 0; chunk--) {
                string data = i->getData().substr(offset, framesize);
                channel.send(AMQContentBody(data)); 
                offset += framesize;
            }
            uint32_t remainder = i->size() % framesize;
            if (remainder) {
                string data = i->getData().substr(offset, remainder);
                channel.send(AMQContentBody(data)); 
            }
        } else {
            channel.send(*i);
        }
    }
}

void InMemoryContent::encode(Buffer& buffer)
{
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        i->encode(buffer);
    }        
}

