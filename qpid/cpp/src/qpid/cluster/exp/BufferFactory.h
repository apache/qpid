#ifndef QPID_CLUSTER_EXP_BUFFERFACTORY_H
#define QPID_CLUSTER_EXP_BUFFERFACTORY_H

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
#include "qpid/BufferRef.h"
#include "qpid/RefCountedBuffer.h"
#include "qpid/sys/Mutex.h"
#include <algorithm>

namespace qpid {
namespace cluster {

/**
 * Factory to allocate sub-buffers of RefCountedBuffers.
 * Thread safe.
 */
class BufferFactory
{
  public:
    /** @param min_size minimum size of underlying buffers. */
    inline BufferFactory(size_t min_size);
    inline BufferRef get(size_t size);
  private:
    sys::Mutex lock;
    size_t min_size;
    BufferRef buf;
    char* pos;
};

BufferFactory::BufferFactory(size_t size) : min_size(size) {}

BufferRef BufferFactory::get(size_t size) {
    sys::Mutex::ScopedLock l(lock);
    if (!buf || pos + size > buf.end()) {
        buf = RefCountedBuffer::create(std::max(size, min_size));
        pos = buf.begin();
    }
    assert(buf);
    assert(buf.begin() <= pos);
    assert(pos + size <= buf.end());
    BufferRef ret(buf.sub_buffer(pos, pos+size));
    pos += size;
    return ret;
}

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_BUFFERFACTORY_H*/
