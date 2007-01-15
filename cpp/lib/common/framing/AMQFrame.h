#ifndef _AMQFrame_
#define _AMQFrame_

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
#include <boost/cast.hpp>

#include <amqp_types.h>
#include <AMQBody.h>
#include <AMQDataBlock.h>
#include <AMQMethodBody.h>
#include <AMQHeaderBody.h>
#include <AMQContentBody.h>
#include <AMQHeartbeatBody.h>
#include <AMQP_MethodVersionMap.h>
#include <AMQP_HighestVersion.h>
#include <Buffer.h>

namespace qpid {
namespace framing {

	
class AMQFrame : virtual public AMQDataBlock
{
  public:
    AMQFrame(qpid::framing::ProtocolVersion& _version = highestProtocolVersion);
    AMQFrame(qpid::framing::ProtocolVersion& _version, u_int16_t channel, AMQBody* body);
    AMQFrame(qpid::framing::ProtocolVersion& _version, u_int16_t channel, const AMQBody::shared_ptr& body);    
    virtual ~AMQFrame();
    virtual void encode(Buffer& buffer); 
    virtual bool decode(Buffer& buffer); 
    virtual u_int32_t size() const;
    u_int16_t getChannel();
    AMQBody::shared_ptr getBody();

    /** Convenience template to cast the body to an expected type */
    template <class T> boost::shared_ptr<T> castBody() {
        assert(dynamic_cast<T*>(getBody().get()));
        boost::static_pointer_cast<T>(getBody());
    }

    u_int32_t decodeHead(Buffer& buffer); 
    void decodeBody(Buffer& buffer, uint32_t size); 

  private:
        static AMQP_MethodVersionMap versionMap;
    qpid::framing::ProtocolVersion version;
            
    u_int16_t channel;
    u_int8_t type;
    AMQBody::shared_ptr body;
            

  friend std::ostream& operator<<(std::ostream& out, const AMQFrame& body);
};

}} // namespace qpid::framing


#endif
