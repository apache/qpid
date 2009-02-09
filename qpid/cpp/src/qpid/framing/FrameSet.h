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
#include "qpid/InlineVector.h"
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/SequenceNumber.h"

#ifndef _FrameSet_
#define _FrameSet_

namespace qpid {
namespace framing {

/**
 * Collects the frames representing a message.
 */
class FrameSet
{
    typedef InlineVector<AMQFrame, 4> Frames;
    const SequenceNumber id;
    Frames parts;
	mutable uint64_t contentSize;
	mutable bool recalculateSize;

public:
    typedef boost::shared_ptr<FrameSet> shared_ptr;

    FrameSet(const SequenceNumber& id);
    void append(const AMQFrame& part);
    bool isComplete() const;

    uint64_t getContentSize() const;

    void getContent(std::string&) const;
    std::string getContent() const;

    bool isContentBearing() const;

    const AMQMethodBody* getMethod() const;
    const AMQHeaderBody* getHeaders() const;
    AMQHeaderBody* getHeaders();
     
    template <class T> bool isA() const {
        const AMQMethodBody* method = getMethod();
        return method && method->isA<T>();
    }

    template <class T> const T* as() const {
        const AMQMethodBody* method = getMethod();
        return (method && method->isA<T>()) ? dynamic_cast<const T*>(method) : 0;
    }    

    template <class T> const T* getHeaderProperties() const {
        const AMQHeaderBody* header = getHeaders();
        return header ? header->get<T>() : 0;
    }

    Frames::const_iterator begin() const { return parts.begin(); }
    Frames::const_iterator end() const { return parts.end(); }
    
    const SequenceNumber& getId() const { return id; }

    template <class P> void remove(P predicate) {
        parts.erase(std::remove_if(parts.begin(), parts.end(), predicate), parts.end());
    }

    template <class F> void map(F& functor) {
        std::for_each(parts.begin(), parts.end(), functor);
    }

    template <class F> void map(F& functor) const {
        std::for_each(parts.begin(), parts.end(), functor);
    }

    template <class F, class P> void map_if(F& functor, P predicate) {
        for(Frames::iterator i = parts.begin(); i != parts.end(); i++) {
            if (predicate(*i)) functor(*i);
        }
    }

    template <class F, class P> void map_if(F& functor, P predicate) const {
        for(Frames::const_iterator i = parts.begin(); i != parts.end(); i++) {
            if (predicate(*i)) functor(*i);
        }
    }
};

}
}


#endif
