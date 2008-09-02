#ifndef QPID_SYS_CONNECTION_CODEC_H
#define QPID_SYS_CONNECTION_CODEC_H

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
#include "qpid/framing/ProtocolVersion.h"

namespace qpid {

namespace sys {

class InputHandlerFactory;
class OutputControl;

/**
 * Interface of coder/decoder for a connection of a specific protocol
 * version.
 */
class ConnectionCodec {
  public:
    virtual ~ConnectionCodec() {}

    /** Decode from buffer, return number of bytes decoded.
     * @return may be less than size if there was incomplete
     * data at the end of the buffer.
     */
    virtual size_t decode(const char* buffer, size_t size) = 0;


    /** Encode into buffer, return number of bytes encoded */
    virtual size_t encode(const char* buffer, size_t size) = 0;

    /** Return true if we have data to encode */
    virtual bool canEncode() = 0;

    /** Network connection was closed from other end. */
    virtual void closed() = 0;
    
    virtual bool isClosed() const = 0;

    virtual framing::ProtocolVersion getVersion() const = 0;
    
    struct Factory {
        virtual ~Factory() {}

        /** Return 0 if version unknown */
        virtual ConnectionCodec* create(
            framing::ProtocolVersion, OutputControl&, const std::string& id
        ) = 0;

        /** Return "preferred" codec for outbound connections. */
        virtual ConnectionCodec* create(
            OutputControl&, const std::string& id
        ) = 0;
    };
};

}} // namespace qpid::sys

#endif  /*!QPID_SYS_CONNECTION_CODEC_H*/
