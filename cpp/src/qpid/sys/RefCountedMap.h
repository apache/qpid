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

#include <map>

namespace qpid {
namespace sys {

/**
 * A thread-safe, RefCounted map of RefCounted entries.  Entries are
 * automatically erased when released The entire map is released when
 * all its entries are erased.
 *
 * The assumption is that some other object/thread holds an iterator
 * to each entry for as long as it is useful.
 *
 * The map can be cleared with close()
 *
 * WARNING: Assigning an intrusive_ptr<D> returned by the map locks the
 * map.  To avoid deadlock, you MUST NOT modify an iterator while
 * holding another lock that could be locked as a result of erasing
 * the entry and destroying its value.
 *
 * @param D must be public RefCounted 
 */
template <class Key, class Data>
class RefCountedMap : public RefCounted
{
  public:
    typedef intrusive_ptr<Data> DataPtr;

  private:
    struct Entry : public Data {
        typedef typename RefCountedMap::Iterator Iterator;
        intrusive_ptr<RefCountedMap> map;
        Iterator self;
        void init(intrusive_ptr<RefCountedMap> m, Iterator s) {
            map=m; self=s;
        }
        void released() const {
            if (map) {
                intrusive_ptr<RefCountedMap> protect(map);
                map->map.erase(self);
            }
        }
    };

    typedef std::map<Key,Entry> Map;
    typedef typename Map::iterator Iterator;

    typedef Mutex::ScopedLock Lock;
    struct OpenLock : public Lock {
        OpenLock(RefCountedMap& m) : Lock(m.lock) { assert(!m.closed); }
    };
    
    DataPtr ptr_(Iterator i) { return i==map.end() ? 0 : &i->second; }

    Mutex lock;
    Map map;
    bool closed;
    
  friend struct Entry;
  friend class iterator;

  public:
    RefCountedMap() : closed(false) {}
    
    /** Return 0 if not found
     * @pre !isClosed()
     */
    DataPtr find(const Key& k) {
        OpenLock l(*this);
        return ptr_(map.find(k));
    }

    /** Find existing or create new entry for k 
     * @pre !isClosed()
     */
    DataPtr get(const Key& k)  {
        OpenLock l(*this);
        std::pair<Iterator,bool> ib=
            map.insert(std::make_pair(k, Entry()));
        if (ib.second)
            ib.first->second.init(this, ib.first);
        return ptr_(ib.first);
    }
    
    size_t size() { Lock l(lock); return map.size(); }

    bool empty() { return size() == 0u; }

    bool isClosed() { Lock l(lock); return closed; }
    
    /**
     * Close the map and call functor on each remaining entry.
     * Note the map will not be deleted until all entries are
     * released, the assumption is that functor takes some
     * action that will release the entry.
     *
     * close() does nothing if isClosed() 
     */
    template <class F>
    void close(F functor) {
        Lock l(lock);
        if (closed) return;
        closed=true;            // No more inserts
        intrusive_ptr<RefCountedMap> protect(this);
        Iterator i=map.begin();
        while (i != map.end()) {
            Iterator j=i;
            ++i;
            functor(j->second); // May erase j
        }
    }
};


}} // namespace qpid::sys

#endif  /*!QPID_SYS_REFCOUNTEDMAP_H*/
