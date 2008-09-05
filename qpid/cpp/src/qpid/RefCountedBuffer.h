#ifndef QPID_REFCOUNTEDBUFFER_H
#define QPID_REFCOUNTEDBUFFER_H

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
#include <boost/intrusive_ptr.hpp>

namespace qpid {

/**
 * Reference-counted byte buffer.
 * No alignment guarantees.
 */
class RefCountedBuffer : boost::noncopyable {
    mutable boost::detail::atomic_count count;
    RefCountedBuffer();
    void destroy() const;
    char* addr() const;

public:

    typedef boost::intrusive_ptr<RefCountedBuffer> intrusive_ptr;

    /** Create a reference counted buffer of size n */
    static intrusive_ptr create(size_t n);

    /** Get a pointer to the start of the buffer. */
    char* get() { return addr(); }
    const char* get() const { return addr(); }
    char& operator[](size_t i) { return get()[i]; }
    const char& operator[](size_t i) const { return get()[i]; }

    void addRef() const { ++count; }
    void release() const { if (--count==0) destroy(); }
    long refCount() { return count; }
};

} // namespace qpid

// intrusive_ptr support.
namespace boost {
inline void intrusive_ptr_add_ref(const qpid::RefCountedBuffer* p) { p->addRef(); }
inline void intrusive_ptr_release(const qpid::RefCountedBuffer* p) { p->release(); }
}


#endif  /*!QPID_REFCOUNTEDBUFFER_H*/
