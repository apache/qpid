#ifndef _posix_AtomicCount_h
#define _posix_AtomicCount_h

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

#include <boost/detail/atomic_count.hpp>
#include <boost/noncopyable.hpp>

namespace qpid {
namespace sys {

/**
 * Atomic counter.
 */
class AtomicCount : boost::noncopyable {
  public:
    class ScopedDecrement : boost::noncopyable {
      public:
        /** Decrement counter in constructor and increment in destructor. */
        ScopedDecrement(AtomicCount& c) : count(c) { value = --count; }
        ~ScopedDecrement() { ++count; }
        /** Return the value returned by the decrement. */
        operator long() { return value; }
      private:
        AtomicCount& count;
        long value;
    };

    class ScopedIncrement : boost::noncopyable {
      public:
        /** Increment counter in constructor and increment in destructor. */
        ScopedIncrement(AtomicCount& c) : count(c) { ++count; }
        ~ScopedIncrement() { --count; }
      private:
        AtomicCount& count;
    };

    AtomicCount(long value = 0) : count(value) {}
    
    void operator++() { ++count ; }
    
    long operator--() { return --count; }
    
    operator long() const { return count; }

    
  private:
    boost::detail::atomic_count  count;
};


}}


#endif // _posix_AtomicCount_h
