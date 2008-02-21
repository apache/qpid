#ifndef QPID_SERIALIZERBASE_H
#define QPID_SERIALIZERBASE_H

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

#include <boost/cast.hpp>
#include <boost/array.hpp>
#include <boost/utility/enable_if.hpp>
#include <boost/type_traits/is_class.hpp>

namespace qpid {

/**
 * Base template for serializers, provides generic serialization for
 * conmpound types and common encode/decode/size functions.
 *
 * Derived template must provide
 * - Derived& op()(T) for primitive types.
 * - Derived& raw(void*, size_t) for raw binary data
 * - Derived& byte(char) for single bytes.
 * 
 * Derived templatse may override any of the functions provided by
 * this base class.
 *
 * This class provides templates to break down compound types 
 * into primitive types and delegate to the derived class.
 *
 */
template <class Derived> class Serializer {
  public:

    /** Call T::serialize() for classes that have their own serialize function */
    template <class T>
    typename boost::enable_if<boost::is_class<T>, Derived>::type
    operator()(T& t) { t.serialize(self()); return self(); }

    template <class T, size_t N>
    Derived& operator()(boost::array<T,N>& a) {
        std::for_each(a.begin(), a.end(), self());
        return self();
    }

    Derived& operator()(char& x) { return self().byte((char&)x); }
    Derived& operator()(int8_t& x) { return self().byte((char&)x); }
    Derived& operator()(uint8_t& x) { return self().byte((char&)x); }

  protected:
    template <class T> Derived& raw(T& t) {
        return self().raw(&t, sizeof(T));
    }

  private:
    Derived& self() { return *static_cast<Derived*>(this); }
};

/** Like Serializer but does not modify the values passed to it. */
template <class Derived> class ConstSerializer {
  public:
    template <class T>
    typename boost::enable_if<boost::is_class<T>, Derived>::type
    operator()(const T& t) {
        // Const cast so we don't have to write 2 serialize() functions
        // for every class.
        const_cast<T&>(t).serialize(self());
        return self();
    }

    template <class T, size_t N>
    Derived& operator()(const boost::array<T,N>& a) {
        std::for_each(a.begin(), a.end(), self());
        return self();
    }

    Derived& operator()(char x) { return self().byte(x); }
    Derived& operator()(int8_t x) { return self().byte(x); }
    Derived& operator()(uint8_t x) { return self().byte(x); }

  protected:
    template <class T> Derived& raw(const T& t) {
        return self().raw(&t, sizeof(T));
    }

  private:
    Derived& self() { return *static_cast<Derived*>(this); }
};


} // namespace qpid

#endif  /*!QPID_SERIALIZERBASE_H*/
