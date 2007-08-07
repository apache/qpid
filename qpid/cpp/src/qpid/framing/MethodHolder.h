#ifndef QPID_FRAMING_METHODBODYHOLDER_H
#define QPID_FRAMING_METHODBODYHOLDER_H

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

#include "qpid/framing/method_variants.h"

#include <boost/type_traits/has_nothrow_copy.hpp>

namespace qpid {
namespace framing {

class AMQMethodBody;

/**
 * Holder for arbitrary method body.
 */
class MethodHolder
{
  public:
    MethodHolder() {}
    MethodHolder(const MethodVariant& mv) : method(mv) {}

    /** Construct from a concrete method body type.
     * ClassVariant<T>::value is the containing class variant.
     */
    template <class T>
    MethodHolder(const T& t)
        : method(typename ClassVariant<T>::value(t)) {}

    /** Construct from class/method ID, set the method accordingly. */
    MethodHolder(ClassId, MethodId);

    /** Set the method to the type corresponding to ClassId, MethodId */
    void setMethod(ClassId, MethodId);
    
    AMQMethodBody* getMethod();
    const AMQMethodBody* getMethod() const;
    
    MethodVariant method;

    void encode(Buffer&) const;
    void decode(Buffer&);
    uint32_t size() const;
};

inline std::ostream& operator<<(std::ostream& out, const MethodHolder& h) {
    return out << h.method;
}


}} // namespace qpid::framing

namespace boost {
template<> struct has_nothrow_copy<qpid::framing::MethodHolder> 
    : public boost::true_type {};
}



#endif  /*!QPID_FRAMING_METHODBODYHOLDER_H*/
