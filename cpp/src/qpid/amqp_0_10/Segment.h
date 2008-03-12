#ifndef QPID_AMQP_0_10_SEGMENT_H
#define QPID_AMQP_0_10_SEGMENT_H

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

#include "Frame.h"
#include "qpid/InlineVector.h"
#include "qpid/amqp_0_10/built_in_types.h"
#include <boost/iterator/iterator_facade.hpp>
#include <boost/range/iterator_range.hpp>
#include <vector>

namespace qpid {
namespace amqp_0_10 {

/**
 * Represents a (possibly incomplete) header or body segment.
 */
class Segment {
  public:
    // TODO aconway 2008-03-07: optimize - use ISlist instead of vector.
    typedef std::vector<Frame> Frames; 
    struct const_iterator;

    Segment();
    
    bool isComplete() const;
    bool isFirst() const;
    bool isLast() const;

    const Frames& getFrames() const { return frames; }

    void add(const Frame& frame);

    /** Mark an empty segment as complete.
     *@pre frames.empty().
     */
    void setMissing() { assert(frames.empty()); missing = true; }

    size_t size() const; ///< Content size.

    /** Iterate over content as a continuous range of bytes. */
    const_iterator begin() const;
    const_iterator end() const;

    // TODO aconway 2008-03-07: Provide output iterator
    // that extends the segment as basis for ostream (XML exchange)
  private:
    bool missing;
    Frames frames;
};

class Segment::const_iterator : public boost::iterator_facade<
    Segment::const_iterator, const char, boost::forward_traversal_tag>
{
  public:
    const_iterator() : frames(), p() {}

  private:

    void invariant() const {
        assert(frames);
        assert(frames->begin() <= i);
        assert(i <= frames->end());
        assert(i->begin() <= p);
        assert(p <= i->end());
    }
    void valid() const {
        invariant();
        assert(p < i->end());
    }

    const_iterator(const Frames& f, Frames::const_iterator pos, const char* ptr)
        : frames(&f), i(pos), p(ptr) { skip_empty(); }

    const char& dereference() const { valid(); return *p; }
    bool equal(const const_iterator& x) const { return p == x.p; }
    void increment() { valid(); ++p; skip_empty(); }
    void advance(ptrdiff_t n) {
        ptrdiff_t r = i->end() - p;
        while (n > r) {
            assert(i != frames->end());
            n -= r;
            p = (++i)->begin();
            r = i->size();
        }
        p += n;
        skip_empty();
    }
    void skip_empty() {
        invariant();
        while (p == i->end() && i != frames->end())
            p = (++i)->begin();
        invariant();
    }

    const Frames* frames;
    Frames::const_iterator i;
    const char* p;

  friend class Segment;
  friend class boost::iterator_core_access;
};


}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_SEGMENT_H*/
