#ifndef QPID_REFCOUNTED_H
#define QPID_REFCOUNTED_H

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

#include <boost/utility.hpp>
#include <boost/detail/atomic_count.hpp>

namespace qpid {

/**
 * Reference-counted base class.
 * Note: this class isn't copyable - you must copy the intrusive_ptr that points
 * to the class that has mixed this in not the class itself (as that would sidestep
 * the reference counting)
 */
class RefCounted : boost::noncopyable {
    mutable boost::detail::atomic_count count;

public:
    RefCounted() : count(0) {}
    void addRef() const { ++count; }
    void release() const { if (--count==0) delete this; }
    long refCount() { return count; }

protected:
    virtual ~RefCounted() {};
};

/**
 * Reference-counted member of a reference-counted parent class.
 * Delegates reference counts to the parent so that the parent is
 * deleted only when there are no references to the parent or any of
 * its children.
 * TODO: Delete this class if it's unused as I don't think this class makes much sense:
 */
struct RefCountedChild {
    RefCounted& parent;

protected:
    RefCountedChild(RefCounted& parent_) : parent(parent_) {}

public:
    void addRef() const { parent.addRef(); }
    void release() const { parent.release(); }
};

} // namespace qpid

// intrusive_ptr support.
namespace boost {
inline void intrusive_ptr_add_ref(const qpid::RefCounted* p) { p->addRef(); }
inline void intrusive_ptr_release(const qpid::RefCounted* p) { p->release(); }
inline void intrusive_ptr_add_ref(const qpid::RefCountedChild* p) { p->addRef(); }
inline void intrusive_ptr_release(const qpid::RefCountedChild* p) { p->release(); }
}


#endif  /*!QPID_REFCOUNTED_H*/
