#ifndef _posix_AtomicCount_h
#define _posix_AtomicCount_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <boost/detail/atomic_count.hpp>
#include <boost/noncopyable.hpp>
#include <boost/function.hpp>

namespace qpid {
namespace sys {

/**
 * Increment counter in constructor and decrement in destructor.
 * Optionally call a function if the decremented counter value is 0.
 * Note the function must not throw, it is called in the destructor.
 */
template <class Count>
class ScopedIncrement : boost::noncopyable {
  public:
    ScopedIncrement(Count& c, boost::function0<void> f=0)
        : count(c), callback(f) { ++count; }
    ~ScopedIncrement() { if (--count == 0 && callback) callback(); }

  private:
    Count& count;
    boost::function0<void> callback;
};

/** Decrement counter in constructor and increment in destructor. */
template <class Count>
class ScopedDecrement : boost::noncopyable {
  public:
    ScopedDecrement(Count& c) : count(c) { value = --count; }
    ~ScopedDecrement() { ++count; }

    /** Return the value after the decrement. */
    operator long() { return value; }

  private:
    Count& count;
    long value;
};


/**
 * Atomic counter.
 */
class AtomicCount : boost::noncopyable {
  public:
    typedef ScopedIncrement<AtomicCount> ScopedIncrement;
    typedef ScopedDecrement<AtomicCount> ScopedDecrement;
    
    AtomicCount(long value = 0) : count(value) {}
    
    void operator++() { ++count ; }
    
    long operator--() { return --count; }
    
    operator long() const { return count; }

    
  private:
    boost::detail::atomic_count  count;
};


}}


#endif // _posix_AtomicCount_h
