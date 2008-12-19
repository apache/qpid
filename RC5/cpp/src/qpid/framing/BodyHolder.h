#ifndef QPID_FRAMING_BODYHOLDER_H
#define QPID_FRAMING_BODYHOLDER_H

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
#include "qpid/framing/Blob.h"
#include "qpid/framing/MaxMethodBodySize.h" // Generated file.
#include "qpid/framing/amqp_types.h"
#include "qpid/RefCounted.h"


namespace qpid {
namespace framing {

class AMQMethodBody;
class AMQBody;
class Buffer;

/**
 * Holder for arbitrary frame body.
 */
class BodyHolder : public RefCounted
{
  public:
    // default copy, assign dtor ok.
    BodyHolder() {}
    BodyHolder(const AMQBody& b) { setBody(b); }
    BodyHolder(ClassId c, MethodId m) { setMethod(c,m); }

    /** Construct from an in_place constructor expression. */
    template <class InPlace>
    BodyHolder(const InPlace& ip, typename EnableInPlace<InPlace>::type* =0)
        : blob(ip) {}

    void setBody(const AMQBody& b);

    /** Assign from an in_place constructor expression. */
    template <class InPlace>
    typename EnableInPlace<InPlace,BodyHolder&>::type
    operator=(const InPlace& ip) { blob=ip; return *this; }

    /** Assign by copying. */
    template <class T>
    typename DisableInPlace<T,BodyHolder&>::type operator=(const T& x)
    { blob=in_place<T>(x); return *this; }

    /** Set to method with ClassId c, MethodId m. */
    void setMethod(ClassId c, MethodId m);

    void encode(Buffer&) const;
    void decode(uint8_t frameType, Buffer&, uint32_t=0);
    uint32_t encodedSize() const;

    /** Return body pointer or 0 if empty. */
    AMQBody* get() { return blob.get(); }
    const AMQBody* get() const { return blob.get(); }

    /** Return method pointer or 0 if not a method. */
    AMQMethodBody* getMethod() { return get()->getMethod(); }
    const AMQMethodBody* getMethod() const { return get()->getMethod(); }

  private:
    Blob<MAX_METHOD_BODY_SIZE, AMQBody> blob;
};

}} // namespace qpid::framing

#endif  /*!QPID_FRAMING_BODYHOLDER_H*/
