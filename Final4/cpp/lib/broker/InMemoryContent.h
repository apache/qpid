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
#ifndef _InMemoryContent_
#define _InMemoryContent_

#include <Content.h>
#include <vector>


namespace qpid {
    namespace broker {
        class InMemoryContent : public Content{
            typedef std::vector<qpid::framing::AMQContentBody::shared_ptr> content_list;
            typedef content_list::iterator content_iterator;

            content_list content;
        public:
            void add(qpid::framing::AMQContentBody::shared_ptr data);
            u_int32_t size();
            void send(qpid::framing::ProtocolVersion& version, qpid::framing::OutputHandler* out, int channel, u_int32_t framesize);
            void encode(qpid::framing::Buffer& buffer);
            void destroy();
            ~InMemoryContent(){}
        };
    }
}


#endif
