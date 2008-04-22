#ifndef QPID_RANGESET_H
#define QPID_RANGESET_H

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

#include "qpid/InlineVector.h"
#include <boost/iterator/iterator_facade.hpp>
#include <boost/operators.hpp>
#include <boost/bind.hpp>
#include <algorithm>

namespace qpid {

/** A range of values, used in RangeSet */
template <class T>
class Range {
  public:
    Range() : begin_(), end_() {}
    explicit Range(const T& t) : begin_(t), end_(t) { ++end_; }
    Range(const T& b, const T& e) : begin_(b), end_(e) { assert(b <= e); }

    T begin() const { return begin_; }
    T end() const { return end_; }

    void begin(const T& t) { begin_ = t; }
    void end(const T& t) { end_ = t; }

    bool empty() const { return begin_ == end_; }

    bool contains(const T& x) const { return begin_ <= x && x < end_; }
    bool contains(const Range& r) const { return begin_ <= r.begin_ && r.end_ <= end_; }

    bool operator==(const Range& x) { return begin_ == x.begin_ && end_== x.end_; }

    bool operator<(const T& t) const { return end_ < t; }

    /** touching ranges can be merged into a single range. */
    bool touching(const Range& r) const {
        return std::max(begin_, r.begin_) <= std::min(end_, r.end_);
    }

    /** @pre touching */
    void merge(const Range& r) {
        assert(touching(r));
        begin_ = std::min(begin_, r.begin_);
        end_ = std::max(end_, r.end_);
    }

    operator bool() const { return !empty(); }
        
  private:
    T begin_, end_;
};


/**
 * A set implemented as a list of [begin, end) ranges.
 * T must be LessThanComparable and Incrementable.
 * RangeSet only provides const iterators.
 */
template <class T>
class RangeSet
    : boost::additive1<RangeSet<T>,
                       boost::additive2<RangeSet<T>, Range<T>,
                                        boost::additive2<RangeSet<T>, T> > >
{
    typedef qpid::Range<T> Range;
    typedef InlineVector<Range, 3> Ranges; // TODO aconway 2008-04-21: what's the optimial inlined value?

  public:
    
    class iterator : public boost::iterator_facade<
        iterator,
        const T,
        boost::forward_traversal_tag>
    {
      public:
        iterator() : ranges(), iter(), value() {}
    
      private:
        typedef typename Ranges::const_iterator RangesIter;
        iterator(const Ranges& r, const RangesIter& i, const T& t)
            : ranges(&r), iter(i), value(t) {}
        
        void increment();
        bool equal(const iterator& i) const;
        const T& dereference() const { return value; }

        const Ranges* ranges;
        RangesIter iter;
        T value;

      friend class RangeSet<T>;
      friend class boost::iterator_core_access;
    };

    typedef iterator const_iterator;
    
    RangeSet() {}
    explicit RangeSet(const Range& r) { ranges.push_back(r); }
    RangeSet(const T& a, const T& b) { ranges.push_back(Range(a,b)); }
    
    bool contiguous() const { return ranges.size() <= 1; }

    bool contains(const T& t) const;
    bool contains(const Range&) const;

    /**@pre contiguous() */
    Range toRange() const;

    bool operator==(const RangeSet<T>&) const;

    void removeRange (const Range&);
    void removeSet (const RangeSet&);
    
    RangeSet& operator+=(const T& t) { return *this += Range(t); }
    RangeSet& operator+=(const Range&);
    RangeSet& operator+=(const RangeSet&) { return *this; };

    RangeSet& operator-=(const T& t)  { return *this -= Range(t); }
    RangeSet& operator-=(const Range& r) { removeRange(r); return *this; }
    RangeSet& operator-=(const RangeSet& s) { removeSet(s); return *this; }

    T front() const { return ranges.front().begin(); }
    T back() const { return ranges.back().end(); }

    iterator begin() const;
    iterator end() const;
    
    bool empty() const { return ranges.empty(); }

    /** Return the largest contiguous range containing x.
     * Returns the empty range [x,x) if x is not in the set.
     */
    Range rangeContaining(const T&) const;

 private:
    Ranges ranges;

  template <class U> friend std::ostream& operator<<(std::ostream& o, const RangeSet<U>& r);

  friend class iterator;
};

template <class T>
std::ostream& operator<<(std::ostream& o, const Range<T>& r) {
    return o << "[" << r.begin() << "," << r.end() << ")";
}

template <class T>
std::ostream& operator<<(std::ostream& o, const RangeSet<T>& rs) {
    std::ostream_iterator<Range<T> > i(o, " ");
    o << "{ ";
    std::copy(rs.ranges.begin(), rs.ranges.end(), i);
    return o << "}";
}

template <class T>
bool RangeSet<T>::contains(const T& t) const {
    typename Ranges::const_iterator i =
        std::lower_bound(ranges.begin(), ranges.end(), t);
    return i != ranges.end() && i->contains(t);
}

template <class T>
bool RangeSet<T>::contains(const Range& r) const {
    typename Ranges::const_iterator i =
        std::lower_bound(ranges.begin(), ranges.end(), r.begin());
    return i != ranges.end() && i->contains(r);
}

template <class T> RangeSet<T>& RangeSet<T>::operator+=(const Range& r) {
    typename Ranges::iterator i =
        std::lower_bound(ranges.begin(), ranges.end(), r.begin());
    if (i != ranges.end() && i->touching(r)) {
        i->merge(r);
        typename Ranges::iterator j = i;
        if (++j != ranges.end() && i->touching(*j)) {
            i->merge(*j);
            ranges.erase(j);
        }
    }
    else
        ranges.insert(i, r);
    return *this;
}

template <class T> void RangeSet<T>::removeRange(const Range& r) {
    typename Ranges::iterator i,j;
    i = std::lower_bound(ranges.begin(), ranges.end(), r.begin());
    if (i == ranges.end() || i->begin() >= r.end())
        return;                 // Outside of set
    if (*i == r)                // Erase i
        ranges.erase(i);
    else if (i->contains(r)) {  // Split i
        Range i1(i->begin(), r.begin());
        Range i2(r.end(), i->end());
        *i = i2;
        ranges.insert(i, i1);
    } else {
        if (i->begin() < r.begin()) { // Truncate i
            i->end(r.begin());
            ++i;
        }
        for (j = i; j != ranges.end() && r.contains(*j); ++j)
            ;                   // Ranges to erase.
        if (j != ranges.end() && j->end() > r.end())
            j->begin(r.end());   // Truncate j
        ranges.erase(i,j);
    }
}

template <class T> void RangeSet<T>::removeSet(const RangeSet& r) {
    std::for_each(
        r.ranges.begin(), r.ranges.end(),
        boost::bind(&RangeSet<T>::removeRange, this, _1));
}

template <class T> Range<T> RangeSet<T>::toRange() const {
    assert(contiguous());
    return empty() ? Range() :  ranges.front();
}

template <class T> void RangeSet<T>::iterator::increment() {
    assert(ranges && iter != ranges->end());
    if (!iter->contains(++value)) {
        ++iter;
        if (iter == ranges->end())
            *this=iterator();   // end() iterator
        else 
            value=iter->begin();
    }
}

template <class T> bool RangeSet<T>::operator==(const RangeSet<T>& r) const {
    return ranges.size() == r.ranges.size() && std::equal(ranges.begin(), ranges.end(), r.ranges.begin());
}

template <class T> typename RangeSet<T>::iterator RangeSet<T>::begin() const {
    return empty() ? end() : iterator(ranges, ranges.begin(), front());
}

template <class T> typename RangeSet<T>::iterator RangeSet<T>::end() const {
    return iterator();
}

template <class T> bool RangeSet<T>::iterator::equal(const iterator& i) const {
    return ranges==i.ranges && (ranges==0 || value==i.value);
}

template <class T> Range<T> RangeSet<T>::rangeContaining(const T& t) const {
    typename Ranges::const_iterator i =
        std::lower_bound(ranges.begin(), ranges.end(), t);
    return (i != ranges.end() && i->contains(t)) ? *i : Range(t,t);
}

} // namespace qpid


#endif  /*!QPID_RANGESET_H*/
