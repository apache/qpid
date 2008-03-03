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

#include <boost/type_traits/remove_const.hpp>
#include <boost/utility/enable_if.hpp>
#include <boost/type_traits/is_const.hpp>
#include <boost/type_traits/is_class.hpp>
#include <boost/type_traits/add_const.hpp>
#include <algorithm>

namespace qpid {

/**
 * Base class for serializers.
 */
template <class Derived> class Serializer {
  public:
    template <class T>
    typename boost::enable_if<boost::is_class<T>, Derived&>::type
    operator()(T& t) {
        // const_cast so we don't need 2 serialize() members for every class.
        const_cast<typename boost::remove_const<T>::type&>(t).serialize(self());
        return self();
    }

    template <class Iter> Derived& iterate(Iter begin, Iter end) {
        std::for_each(begin, end, self());
        return self();
    }

  private:
    Derived& self() { return *static_cast<Derived*>(this); }
};

} // namespace qpid

#endif  /*!QPID_SERIALIZER_H*/
