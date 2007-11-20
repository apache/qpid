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
#include <InMemoryContent.h>

using namespace qpid::broker;
using namespace qpid::framing;
using boost::static_pointer_cast;

void InMemoryContent::add(AMQContentBody::shared_ptr data)
{
    content.push_back(data);
}

u_int32_t InMemoryContent::size()
{
    int sum(0);
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        sum += (*i)->size();
    }
    return sum;
}

void InMemoryContent::send(qpid::framing::ProtocolVersion& version, OutputHandler* out, int channel, u_int32_t framesize)
{
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        if ((*i)->size() > framesize) {
            u_int32_t offset = 0;
            for (int chunk = (*i)->size() / framesize; chunk > 0; chunk--) {
                string data = (*i)->getData().substr(offset, framesize);
                out->send(new AMQFrame(version, channel, new AMQContentBody(data)));                
                offset += framesize;
            }
            u_int32_t remainder = (*i)->size() % framesize;
            if (remainder) {
                string data = (*i)->getData().substr(offset, remainder);
                out->send(new AMQFrame(version, channel, new AMQContentBody(data)));                
            }
        } else {
            AMQBody::shared_ptr contentBody = static_pointer_cast<AMQBody, AMQContentBody>(*i);
            out->send(new AMQFrame(version, channel, contentBody));
        }
    }
}

void InMemoryContent::encode(Buffer& buffer)
{
    for (content_iterator i = content.begin(); i != content.end(); i++) {
        (*i)->encode(buffer);
    }        
}

void InMemoryContent::destroy()
{
}
