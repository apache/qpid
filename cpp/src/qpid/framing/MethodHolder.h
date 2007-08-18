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

#include "qpid/framing/AMQBody.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/Blob.h"
#include "qpid/framing/MethodHolderMaxSize.h" // Generated file.

#include <boost/type_traits/is_base_and_derived.hpp>
#include <boost/utility/enable_if.hpp>

#include <utility>

namespace qpid {
namespace framing {

class AMQMethodBody;
class AMQBody;
class Buffer;

class MethodHolder;
std::ostream& operator<<(std::ostream& out, const MethodHolder& h);

/**
 * Holder for arbitrary method body.
 */
// TODO aconway 2007-08-14: Fix up naming, this class should really be
// called AMQMethodBody and use a different name for the root of
// the concrete method body tree, which should not inherit AMQBody.
// 
class MethodHolder
{
    template <class T> struct EnableIfMethod:
        public boost::enable_if<boost::is_base_and_derived<AMQMethodBody,T>,T>
    {};

    template <class T> EnableIfMethod<T>& assertMethod(T& t) { return t; }
        
  public:
    MethodHolder() {}
    MethodHolder(ClassId& c, MethodId& m) { construct(c,m); }

    /** Construct with a copy of a method body. */
    MethodHolder(const AMQMethodBody& m) { *this = m; }

    /** Copy method body into holder. */
    MethodHolder& operator=(const AMQMethodBody&);

    /** Construct the method body corresponding to class/method id */
    void construct(ClassId c, MethodId m);

    uint8_t type() const { return 1; }
    void encode(Buffer&) const;
    void decode(Buffer&);
    uint32_t size() const;

    /** Return method pointer or 0 if empty. */
    AMQMethodBody* get();
    const AMQMethodBody* get() const;

    /** True if no method has been set */
    bool empty() const { return blob.empty(); }

  private:
    Blob<MAX_METHODBODY_SIZE> blob;
    class CopyVisitor;
  friend struct CopyVisitor;
};



}} // namespace qpid::framing

#endif  /*!QPID_FRAMING_METHODHOLDER_H*/
