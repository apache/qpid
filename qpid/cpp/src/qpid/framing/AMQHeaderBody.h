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
#include "amqp_types.h"
#include "AMQBody.h"
#include "Buffer.h"
#include "BasicHeaderProperties.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/MessageProperties.h"
#include <iostream>
#include <vector>
#include <boost/variant.hpp>
#include <boost/variant/get.hpp>

#ifndef _AMQHeaderBody_
#define _AMQHeaderBody_

namespace qpid {
namespace framing {

class AMQHeaderBody :  public AMQBody
{
    typedef std::vector< boost::variant<BasicHeaderProperties, DeliveryProperties, MessageProperties> > PropertyList; 

    PropertyList properties;

    void decode(BasicHeaderProperties s, Buffer& b, uint32_t size) {
        s.decode(b, size);
        properties.push_back(s);
    }

    template <class T> void decode(T t, Buffer& b, uint32_t size) {
        t.decodeStructBody(b, size);
        properties.push_back(t);
    }

    class Encode : public boost::static_visitor<> {
        Buffer& buffer;
    public:
        Encode(Buffer& b) : buffer(b) {}

        template <class T> void operator()(T& t) const {
            t.encode(buffer);
        }

        void operator()(const BasicHeaderProperties& s) const {
            buffer.putLong(s.size() + 2/*typecode*/);
            buffer.putShort(BasicHeaderProperties::TYPE);           
            s.encode(buffer);
        }
    };

    class CalculateSize : public boost::static_visitor<> {
        uint32_t size;
    public:
        CalculateSize() : size(0) {}

        template <class T> void operator()(T& t) {
            size += t.size();
        }

        void operator()(const BasicHeaderProperties& s) {
            size += s.size() + 2/*typecode*/ + 4/*size field*/;
        }

        uint32_t totalSize() { 
            return size; 
        }        
    };

    class Print : public boost::static_visitor<> {
        std::ostream& out;
    public:
        Print(std::ostream& o) : out(o) {}

        template <class T> void operator()(T& t) {
            out << t;
        }
    };

public:

    AMQHeaderBody();
    ~AMQHeaderBody();
    inline uint8_t type() const { return HEADER_BODY; }

    uint32_t size() const;
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer, uint32_t size);
    uint64_t getContentLength() const;
    void print(std::ostream& out) const;

    void accept(AMQBodyConstVisitor& v) const { v.visit(*this); }

    template <class T> T* get(bool create) { 
        for (PropertyList::iterator i = properties.begin(); i != properties.end(); i++) {
            T* p = boost::get<T>(&(*i));
            if (p) return p;
        }
        if (create) {
            properties.push_back(T());
            return boost::get<T>(&(properties.back()));
        } else {
            return 0;
        }
    }

    template <class T> const T* get() const { 
        for (PropertyList::const_iterator i = properties.begin(); i != properties.end(); i++) {
            const T* p = boost::get<T>(&(*i));
            if (p) return p;
        }
        return 0;
    }
};

}
}


#endif
