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
#include <vector>
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/SequenceNumber.h"
#include "ClientMessage.h"

#ifndef _ReceivedContent_
#define _ReceivedContent_

namespace qpid {
namespace client {

/**
 * Collects the frames representing some received 'content'. This
 * provides a raw interface to 'message' data and attributes.
 */
class ReceivedContent
{
    const framing::SequenceNumber id;
    std::vector<framing::AMQFrame> parts;

public:
    typedef boost::shared_ptr<ReceivedContent> shared_ptr;

    ReceivedContent(const framing::SequenceNumber& id);
    void append(framing::AMQBody* part);
    bool isComplete() const;

    uint64_t getContentSize() const;
    std::string getContent() const;

    const framing::AMQMethodBody* getMethod() const;
    const framing::AMQHeaderBody* getHeaders() const;
     
    template <class T> bool isA() const {
        const framing::AMQMethodBody* method=getMethod();
        return method && method->isA<T>();
    }

    template <class T> const T* as() const {
        const framing::AMQMethodBody* method=getMethod();
        return (method && method->isA<T>()) ? dynamic_cast<const T*>(method) : 0;
    }    

    const framing::SequenceNumber& getId() const { return id; }

    void populate(Message& msg);
};

}
}


#endif
