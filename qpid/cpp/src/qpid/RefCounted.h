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

#include "qpid/sys/AtomicCount.h"

#include <boost/intrusive_ptr.hpp>


namespace qpid {

/** Abstract interface for reference counted objects */
class AbstractRefCounted {
  public:
    virtual void addRef() const=0;
    virtual void release() const=0;
  protected:
    virtual ~AbstractRefCounted() {}
};

/**
 * Reference-counted base class.
 */
class RefCounted : public AbstractRefCounted {
  public:
    RefCounted() {}
    virtual void addRef() const { ++count; }
    virtual void release() const { if (--count==0) released(); }

  protected:
    virtual ~RefCounted() {};
    // Copy/assign do not copy refcounts. 
    RefCounted(const RefCounted&) : AbstractRefCounted() {}
    RefCounted& operator=(const RefCounted&) { return *this; }
    virtual void released() const { assert(count==0); delete this; }

    mutable sys::AtomicCount count;
};

/**
 * Reference-counted member of a reference-counted parent class.
 * Delegates reference counts to the parent so that the parent is
 * deleted only when there are no references to the parent or any of
 * its children.
 */
struct RefCountedChild : public AbstractRefCounted {
  protected:
    AbstractRefCounted& parent;
    RefCountedChild(AbstractRefCounted& parent_) : parent(parent_) {}
  public:
    void addRef() const { parent.addRef(); }
    void release() const { parent.release(); }
};

using boost::intrusive_ptr;

} // namespace qpid

// intrusive_ptr support.
namespace boost {
inline void intrusive_ptr_add_ref(const qpid::AbstractRefCounted* p) { p->addRef(); }
inline void intrusive_ptr_release(const qpid::AbstractRefCounted* p) { p->release(); }
}


#endif  /*!QPID_REFCOUNTED_H*/
