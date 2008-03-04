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
#include <boost/static_assert.hpp>
#include <boost/type_traits/is_class.hpp>
#include <algorithm>

namespace qpid {

// FIXME aconway 2008-03-03: Doc - esp decoding
template <class Derived> class Serializer {
  public:
    typedef Serializer result_type; // unary functor requirement.

    static const bool IS_DECODER=false;

    /** Generic handler for class objects, call serialize() */
    template <class T>
    typename boost::enable_if<boost::is_class<T>, Derived&>::type
    operator()(T& t) {
        t.serialize(self());
        return self();
    }

    /** Generic handler for const class objects, call serialize() */
    template <class T>
    typename boost::enable_if<boost::is_class<T>, Derived&>::type
    operator()(const T& t) {
        assert(!Derived::IS_DECODER); // We won't modify the value.
        // const_cast so we don't need 2 serialize() members for every class.
        const_cast<T&>(t).serialize(self());
        return self();
    }

    template <class T, bool=false> struct Split {
        Split(Derived& s, T& t) { t.encode(s); }
    };
    
    template <class T> struct Split<T,true> {
        Split(Derived& s, T& t) { t.decode(s); }
    };
    /**
     * Called by classes that want to receive separate
     * encode()/decode() calls.
     */
    template <class T>
    void split(T& t) { Split<T, Derived::IS_DECODER>(self(),t); }

  private:
    Derived& self() { return *static_cast<Derived*>(this); }
};





} // namespace qpid

#endif  /*!QPID_SERIALIZER_H*/
