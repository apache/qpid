#ifndef QPID_BUFFERREF_H
#define QPID_BUFFERREF_H

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

#include "qpid/RefCounted.h"
#include <boost/intrusive_ptr.hpp>

namespace qpid {

/** Template for mutable or const buffer references */
template <class T> class BufferRefT {
  public:
    BufferRefT() : begin_(0), end_(0) {}

    BufferRefT(boost::intrusive_ptr<RefCounted> c, T* begin, T* end) :
        counter(c), begin_(begin), end_(end) {}

    template <class U> BufferRefT(const BufferRefT<U>& other) :
        counter(other.counter), begin_(other.begin_), end_(other.end_) {}

    T* begin() const { return begin_; }
    T* end() const { return end_; }

    /** Return a sub-buffer of the current buffer */
    BufferRefT sub_buffer(T* begin, T* end) {
        assert(begin_ <= begin && begin <= end_);
        assert(begin_ <= end && end <= end_);
        assert(begin <= end);
        return BufferRefT(counter, begin, end);
    }

  private:
    boost::intrusive_ptr<RefCounted> counter;
    T* begin_;
    T* end_;
};

/**
 * Reference to a mutable ref-counted buffer.
 */
typedef BufferRefT<char> BufferRef;

/**
 * Reference to a const ref-counted buffer.
 */
typedef BufferRefT<const char> ConstBufferRef;

} // namespace qpid

#endif  /*!QPID_BUFFERREF_H*/
