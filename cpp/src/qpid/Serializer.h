#ifndef QPID_SERIALIZER_H
#define QPID_SERIALIZER_H

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

#include <boost/utility/enable_if.hpp>
#include <boost/type_traits/is_base_and_derived.hpp>

namespace qpid {
namespace serialize { 

/** Wrapper to pass serializer functors by reference. */
template <class S> struct SRef {
    S& s;
    SRef(S& ss) : s(ss) {}
    template <class T> typename S::result_type operator()(T& x) { return s(x); }
    template <class T> typename S::result_type operator()(const T& x) { return s(x); }
};

template <class S> SRef<S> ref(S& s) { return SRef<S>(s); }

// FIXME aconway 2008-03-03: Document.
// Encoder/Decoder concept: add op() for primitive types, raw(),
// op()(Iter, Iter). Note split, encode, decode.
// 

// FIXME aconway 2008-03-09: document - non-intrusive serialzation.
// Default rule calls member. Enums must provide an override rule.

/** Overload for types that do not provide a serialize() member.*/
template <class T> T& serializable(T& t) { return t; }

template <class Derived> class Encoder {
  public:
    typedef Derived& result_type; // unary functor requirement.

    /** Default op() calls serializable() free function */
    template <class T>
    Derived& operator()(const T& t) {
        serializable(const_cast<T&>(t)).serialize(self()); return self();
    }

    /** Split serialize() into encode()/decode() */
    template <class T>
    Derived& split(const T& t) { t.encode(self()); return self(); }
    
  private:
    Derived& self() { return *static_cast<Derived*>(this); }
};

template <class Derived> class Decoder {
  public:
    typedef Derived& result_type; // unary functor requirement.

    /** Default op() calls serializable() free function */
    template <class T>
    Derived& operator()(T& t) {
        serializable(t).serialize(self()); return self();
    }

    /** Split serialize() into encode()/decode() */
    template <class T>
    Derived& split(T& t) { t.decode(self()); return self(); }
    

  private:
    Derived& self() { return *static_cast<Derived*>(this); }
};

/** Serialize a type by converting it to/from another type */
template <class Type, class AsType>
struct SerializeAs {
    Type& value;
    SerializeAs(Type & t) : value(t) {}
    template <class S> void serialize(S& s) { s.split(*this); }
    template <class S> void encode(S& s) const { s(AsType(value)); }
    template <class S> void decode(S& s) { AsType x; s(x); value=x; }
};

}} // namespace qpid::serialize

// FIXME aconway 2008-03-09: rename to serialize.h
// 
#endif  /*!QPID_SERIALIZER_H*/
