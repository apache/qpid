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
#include <boost/type_traits/remove_pointer.hpp>
#include <boost/bind.hpp>
#include <boost/tuple/tuple.hpp>
#include <boost/cast.hpp>
#include <boost/intrusive_ptr.hpp>
#include <vector>
#include <map>
#include <algorithm>

namespace qpid {
namespace sys {

template <class, class, class> class RefCountedMap;

template <class Key, class Data, class Base=RefCounted,
          class Impl=std::map<Key, Data*> >
class RefCountedMapData : public Base {
  public:
    typedef RefCountedMap<Key, Data, Impl> Map;

    bool attached() {
        assert(map || self->second == this);
        return map;
    }
    const Key& getKey() const { assert(attached()); return self->first; }
    void released() const { if (map) map->lockedDetach(self); delete this; }

  private:
      friend class RefCountedMap<Key, Data, Impl>;
    boost::intrusive_ptr<Map> map;
    typename Impl::iterator self;

  public:
};

/**
 * A thread-safe, reference-counted, weak map of reference-counted values.
 * 
 * The map does not hold a reference to its members, they must have
 * external references to exist.  When last external reference is
 * released, the value is atomically erased from the map and deleted.
 *
 * The map itself is a member of a ref-counted holder class, the
 * map ensures its holder is not deleted till the map is empty.
 */
template <class Key, class Data, class Impl=std::map<Key, Data*> >
class RefCountedMap : public RefCountedChild {
  template <class, class, class, class> friend class RefCountedMapData;
    typedef typename Impl::iterator iterator;
    typedef typename Impl::value_type value_type;
    
    mutable sys::Mutex lock;
    Impl map;

    // Acquire the lock and ensure map is not deleted before unlock.
    class Lock {
        boost::intrusive_ptr<const RefCountedMap> map;
        sys::Mutex::ScopedLock lock;
      public:
        Lock(const RefCountedMap* m) : map(m), lock(m->lock) {}
    };

    // Called from Data::released.
    void lockedDetach(iterator i) { Lock l(this); detach(i); }
    
    void detach(iterator i) {
        // Must be called with lock held.
        assert(i->second->map == this);
        map.erase(i);
        i->second->map = 0; // May call this->release()
    }

  public:
    RefCountedMap(RefCounted& container) : RefCountedChild(container) {}
    ~RefCountedMap() {}
    
    /** Return 0 if not found */
    boost::intrusive_ptr<Data> find(const Key& k) {
        Lock l(this);
        iterator i = map.find(k);
        return (i == map.end()) ? 0 : i->second;
    }

    bool insert(const Key& k, boost::intrusive_ptr<Data> d) {
        Lock l(this);
        iterator i;
        bool inserted;
        boost::tuples::tie(i, inserted) =
            map.insert(std::make_pair(k, d.get()));
        if (inserted)  {
            assert(!d->map);
            d->map=boost::polymorphic_downcast<RefCountedMap*>(this);
            d->self=i;
        }
        return inserted;
    }
    
    size_t size() { Lock l(this); return map.size(); }

    bool empty() { Lock l(this); return map.empty(); }

    void erase(const Key& k) { Lock l(this); detach(map.find(k)); }

    void clear() { Lock l(this); while (!map.empty()) detach(map.begin()); }

    /** Clear the map, apply functor to each entry before erasing */
    template <class F> void clear(F functor) {
        Lock l(this);
        while (!map.empty()) {
            boost::intrusive_ptr<Data> ptr;
            if (map.empty()) return;
            ptr = map.begin()->second;
            detach(map.begin());
            sys::Mutex::ScopedUnlock u(lock);
            functor(ptr);
        }
    }

    /** Apply functor to each map entry. */
    template <class F> void apply(F functor) {
        std::vector<boost::intrusive_ptr<Data> > snapshot;
        {
            // Take a snapshot referencing all values in map.
            Lock l(this);
            snapshot.resize(map.size());
            typedef value_type value_type;
            std::transform(map.begin(), map.end(), snapshot.begin(),
                           boost::bind(&value_type::second, _1));
        }
        // Drop the lock to call functor.
        std::for_each(snapshot.begin(), snapshot.end(), functor);
    }
};

    
}} // namespace qpid::sys

#endif  /*!QPID_SYS_REFCOUNTEDMAP_H*/
