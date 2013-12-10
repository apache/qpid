#ifndef QPID_FRAMING_RESIZABLEBUFFER_H
#define QPID_FRAMING_RESIZABLEBUFFER_H

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

#include "qpid/framing/Buffer.h"
#include <vector>

namespace qpid {
namespace framing {

/**
 * A buffer that maintains its own storage and can be resized, 
 * keeping any data already written to the buffer. 
 */
class ResizableBuffer : public Buffer
{
  public:
    ResizableBuffer(size_t initialSize) : store(initialSize) {
        static_cast<Buffer&>(*this) = Buffer(&store[0], store.size());
    }
    
    void resize(size_t newSize) {
        size_t oldPos =  getPosition();
        store.resize(newSize);
        static_cast<Buffer&>(*this) = Buffer(&store[0], store.size());
        setPosition(oldPos);
    }

    /** Make sure at least n bytes are available */
    void makeAvailable(size_t n) {
        if (n > available())
            resize(getSize() + n - available());
    }
    
  private:
    std::vector<char> store;
};
}} // namespace qpid::framing

#endif  /*!QPID_FRAMING_RESIZABLEBUFFER_H*/
