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

#include <qpid/RefCounted.h>
#include <qpid/BufferRef.h>

namespace qpid {

/**
 * Reference-counted byte buffer. Alignment guarantees:
 * The RefCountedBuffer structure is aligned to the 
 *   refCountedBUfferStructAlign byte boundary specified here.
 * The buffer itself has no alignment guarantees.
 */

static const size_t refCountedBufferStructAlign = 8;

class RefCountedBuffer : public RefCounted {
  public:
    /** Create a reference counted buffer of size n */
    static BufferRef create(size_t n);

  protected:
    void released() const;

    size_t alignPad;
};

} // namespace qpid

#endif  /*!QPID_REFCOUNTEDBUFFER_H*/
