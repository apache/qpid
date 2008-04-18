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
#ifndef _framing_SequenceSet_h
#define _framing_SequenceSet_h

#include "amqp_types.h"
#include "Buffer.h"
#include "SequenceNumber.h"
#include "qpid/framing/reply_exceptions.h"
#include <ostream>
#include <list>

namespace qpid {
namespace framing {

class SequenceSet {
    struct Range {
        SequenceNumber start;
        SequenceNumber end;
        
        Range(SequenceNumber s=0, SequenceNumber e=0);
        bool contains(SequenceNumber i) const;
        bool intersects(const Range& r) const;
        bool merge(const Range& r);
        bool mergeable(const SequenceNumber& r) const;
        void encode(Buffer& buffer) const;

        template <class S> void serialize(S& s) { s(start)(end); }
    };

    typedef std::list<Range> Ranges;
    Ranges ranges;

  public:
    SequenceSet() {}
    SequenceSet(const SequenceNumber& s) { add(s); }

    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer);
    uint32_t size() const;   

    bool contains(const SequenceNumber& s) const;
    void add(const SequenceNumber& s);
    void add(const SequenceNumber& start, const SequenceNumber& end);
    void add(const SequenceSet& set);
    void remove(const SequenceNumber& s);
    void remove(const SequenceNumber& start, const SequenceNumber& end);
    void remove(const SequenceSet& set);

    void clear();
    bool empty() const;

    template <class T>
    void for_each(T& t) const
    {
        for (Ranges::const_iterator i = ranges.begin(); i != ranges.end(); i++) {
            t(i->start, i->end);
        }
    }

    template <class S> void serialize(S& s) { s.split(*this); s(ranges.begin(), ranges.end()); }
    template <class S> void encode(S& s) const { s(uint16_t(ranges.size()*sizeof(Range))); }
    template <class S> void decode(S& s) { uint16_t sz; s(sz); ranges.resize(sz/sizeof(Range)); }
    
  friend std::ostream& operator<<(std::ostream&, const SequenceSet&);
};    


}} // namespace qpid::framing


#endif
