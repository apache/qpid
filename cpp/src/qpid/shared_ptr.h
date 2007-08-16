#ifndef _common_shared_ptr_h
#define _common_shared_ptr_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <boost/shared_ptr.hpp>
#include <boost/cast.hpp>

namespace qpid {

// Import shared_ptr definitions into qpid namespace and define some
// useful shared_ptr templates for convenience.

using boost::shared_ptr;
using boost::dynamic_pointer_cast;
using boost::static_pointer_cast;
using boost::const_pointer_cast;
using boost::shared_polymorphic_downcast;

template <class T> shared_ptr<T> make_shared_ptr(T* ptr) {
    return shared_ptr<T>(ptr);
}

template <class T, class D>
shared_ptr<T> make_shared_ptr(T* ptr, D deleter) {
    return shared_ptr<T>(ptr, deleter);
}

inline void nullDeleter(void const *) {} 

} // namespace qpid



#endif  /*!_common_shared_ptr_h*/
