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
#ifndef _Content_
#define _Content_

#include <boost/function.hpp>

#include "qpid/framing/AMQContentBody.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/OutputHandler.h"

namespace qpid {

namespace framing {
class ChannelAdapter;
}

namespace broker {
class Content{
  public:
    typedef std::string DataBlock;
    typedef boost::function1<void, const DataBlock&> SendFn;

    virtual ~Content(){}
    
    /** Add a block of data to the content */
    virtual void add(framing::AMQContentBody::shared_ptr data) = 0;

    /** Total size of content in bytes */
    virtual uint32_t size() = 0;

    /**
     * Iterate over the content calling SendFn for each block.
     * Subdivide blocks if necessary to ensure each block is
     * <= framesize bytes long.
     */
    virtual void send(framing::ChannelAdapter& channel, uint32_t framesize) = 0;

    //FIXME aconway 2007-02-07: This is inconsistently implemented
    //find out what is needed.
    virtual void encode(qpid::framing::Buffer& buffer) = 0;
};
}}


#endif
