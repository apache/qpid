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
#include <LazyLoadedContent.h>

using namespace qpid::broker;
using namespace qpid::framing;

LazyLoadedContent::LazyLoadedContent(MessageStore* const _store, Message* const _msg, u_int64_t _expectedSize) : 
    store(_store), msg(_msg), expectedSize(_expectedSize) {}

void LazyLoadedContent::add(AMQContentBody::shared_ptr data)
{
    store->appendContent(msg, data->getData());
}

u_int32_t LazyLoadedContent::size()
{
    return 0;//all content is written as soon as it is added
}

void LazyLoadedContent::send(qpid::framing::ProtocolVersion& version, OutputHandler* out, int channel, u_int32_t framesize)
{
    if (expectedSize > framesize) {        
        for (u_int64_t offset = 0; offset < expectedSize; offset += framesize) {            
            u_int64_t remaining = expectedSize - offset;
            string data;
            store->loadContent(msg, data, offset, remaining > framesize ? framesize : remaining);              
            out->send(new AMQFrame(version, channel, new AMQContentBody(data)));
        }
    } else {
        string data;
        store->loadContent(msg, data, 0, expectedSize);  
        out->send(new AMQFrame(version, channel, new AMQContentBody(data)));
    }
}

void LazyLoadedContent::encode(Buffer&)
{
    //do nothing as all content is written as soon as it is added 
}

void LazyLoadedContent::destroy()
{
    store->destroy(msg);
}
