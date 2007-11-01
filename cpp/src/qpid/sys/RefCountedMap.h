#ifndef QPID_SYS_REFCOUNTEDMAP_H
#define QPID_SYS_REFCOUNTEDMAP_H

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

#include "qpid/sys/Mutex.h"
#include "qpid/RefCounted.h"

#include <boost/call_traits.hpp>
#include <boost/iterator/iterator_facade.hpp>

#include <map>

namespace qpid {
namespace sys {

/**
 * A thread-safe, RefCounted map of RefCounted entries.  Entries are
 * automatically erased when all iterators to them are destroyed.  The
 * entire map is released when all its entries are erased.
 *
 * The assumption is that some other object/thread holds an iterator
 * to each entry for as long as it is useful.
 *
 * API is a subset of std::map
 *
 * WARNING: Modifying iterators locks the map.  To avoid deadlock, you
 * MUST NOT modify an iterator while holding another lock that could be
 * locked as a result of erasing the entry and destroying its value.
 *
 */

template <class K, class D>
class RefCountedMap : public RefCounted
{
    typedef Mutex::ScopedLock Lock;

  public:
    typedef K key_type;
    typedef D data_type;
    typedef std::pair<key_type,data_type> value_type;

    /** Bidirectional iterator maintains a reference count on the map entry.
     * Provides operator!() and operator bool() to test for end() iterator.
     */
    class iterator : 
        public boost::iterator_facade<iterator, value_type,
                                      boost::bidirectional_traversal_tag>
    {
      public:
        iterator() {}
        bool operator!() const { return !ptr; }
        operator bool() const { return ptr; }
        void reset() { ptr=0; }

      private:
        typedef typename RefCountedMap::Entry Entry;

        iterator(intrusive_ptr<Entry> entry) : ptr(entry) {}

        // boost::iterator_facade functions.
        value_type& dereference() const { return ptr->value; }
        bool equal(iterator j) const { return ptr==j.ptr; }
        void increment() { assert(ptr); *this=ptr->map->next(ptr->self); }
        void decrement() { assert(ptr); *this=ptr->map->prev(ptr->self); }

        intrusive_ptr<Entry> ptr;

      friend class boost::iterator_core_access;
      friend class RefCountedMap<K,D>;
    };

    iterator begin() { Lock l(lock); return makeIterator(map.begin()); }

    iterator end() { Lock l(lock); return makeIterator(map.end()); }

    size_t size() { Lock l(lock); return map.size(); }

    bool empty() { return size() == 0u; }
    
    iterator find(const key_type& key) {
        Lock l(lock); return makeIterator(map.find(key));
    }

    std::pair<iterator, bool> insert(const value_type& x) {
        Lock l(lock);
        std::pair<typename Map::iterator,bool> ib=
            map.insert(make_pair(x.first, Entry(x, this)));
        ib.first->second.self = ib.first;
        return std::make_pair(makeIterator(ib.first), ib.second);
    }

  private:

    //
    // INVARIANT:
    //  - All entries in the map have non-0 refcounts.
    //  - Each entry holds an intrusive_ptr to the map 
    //

    struct Entry : public RefCounted {
        typedef typename RefCountedMap::Map::iterator Iterator;

        Entry(const value_type& v, RefCountedMap* m) : value(v), map(m) {}
        
        value_type value;
        intrusive_ptr<RefCountedMap> map;
        Iterator self;

        // RefCounts are modified with map locked. 
        struct MapLock : public Lock {
            MapLock(RefCountedMap& m) : Lock(m.lock) {}
        };

        void released() const {
            intrusive_ptr<RefCountedMap> protect(map);
            map->map.erase(self);
        }
    };

    typedef std::map<K,Entry> Map;

    iterator makeIterator(typename Map::iterator i) {
        // Call with lock held.
        return iterator(i==map.end() ? 0 : &i->second);
    }

     void erase(typename RefCountedMap::Map::iterator i) {
        // Make sure this is not deleted till lock is released.
        intrusive_ptr<RefCountedMap> self(this);
        { Lock l(lock); map.erase(i); }
    }

    iterator next(typename RefCountedMap::Map::iterator i) {
        { Lock l(lock); return makeIterator(++i); }
    }

    iterator prev(typename RefCountedMap::Map::iterator i) {
        { Lock l(lock); return makeIterator(--i); }
    }

    Mutex lock;
    Map map;

  friend struct Entry;
  friend class iterator;
};


}} // namespace qpid::sys

#endif  /*!QPID_SYS_REFCOUNTEDMAP_H*/
