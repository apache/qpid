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
#ifndef _framing_FramingContent_h
#define _framing_FramingContent_h

#include <ostream>

namespace qpid {
namespace framing {

class Buffer;

enum discriminator_types { INLINE = 0, REFERENCE = 1 };

/**
 * A representation of the AMQP Content data type (used for message
 * bodies) which can hold inline data or a reference.
 */
class Content
{
    uint8_t discriminator;
    string value;

    void validate();

 public:
    Content();
    Content(uint8_t _discriminator, const string& _value);
    ~Content();
  
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer);
    size_t size() const;
    bool isInline() const { return discriminator == INLINE; }
    bool isReference() const { return discriminator == REFERENCE; }
    const string& getValue() const { return value; }
    void setValue(const string& newValue) { value = newValue; }

    friend std::ostream& operator<<(std::ostream&, const Content&);
};    

}} // namespace qpid::framing


#endif  /*!_framing_FramingContent_h*/
