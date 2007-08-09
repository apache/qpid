#ifndef QPID_FRAMING_METHODHOLDER_H
#define QPID_FRAMING_METHODHOLDER_H

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

#include "qpid/framing/amqp_types.h"
#include "qpid/framing/Blob.h"
#include "qpid/framing/MethodHolderMaxSize.h" // Generated file.

#include <utility>

namespace qpid {
namespace framing {

class AMQMethodBody;
class Buffer;

/**
 * Holder for arbitrary method body.
 */
class MethodHolder
{
  public:
    typedef std::pair<ClassId, MethodId> Id; 

    template <class T>static Id idOf() {
        return std::make_pair(T::CLASS_ID, T::METHOD_ID); }

    MethodHolder() {}
    MethodHolder(const Id& id) { construct(id); }
    MethodHolder(ClassId& c, MethodId& m) { construct(std::make_pair(c,m)); }

    template <class M>
    MethodHolder(const M& m) : blob(m), id(idOf<M>()) {}

    template <class M>
    MethodHolder& operator=(const M& m) { blob=m; id=idOf<M>(); return *this; }

    /** Construct the method body corresponding to Id */
    void construct(const Id&);

    void encode(Buffer&) const;
    void decode(Buffer&);
    uint32_t size() const;
            
    AMQMethodBody* get() {
        return static_cast<AMQMethodBody*>(blob.get());
    }
    const AMQMethodBody* get() const {
        return static_cast<const AMQMethodBody*>(blob.get());
    }

    AMQMethodBody* operator* () { return get(); }
    const AMQMethodBody* operator*() const { return get(); }
    AMQMethodBody* operator-> () { return get(); }
    const AMQMethodBody* operator->() const { return get(); }

  private:
    Blob<MAX_METHODBODY_SIZE> blob;
    Id id;
};

std::ostream& operator<<(std::ostream& out, const MethodHolder& h);

}} // namespace qpid::framing

#endif  /*!QPID_FRAMING_METHODHOLDER_H*/
