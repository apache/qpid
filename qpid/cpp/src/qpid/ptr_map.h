#ifndef QPID_PTR_MAP
#define QPID_PTR_MAP

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

#include <boost/ptr_container/ptr_map.hpp>
#include <boost/version.hpp>

namespace qpid {
namespace ptr_map {

/** @file
 * Workaround for API change between boost 1.33 and 1.34.
 *
 * To be portable across these versions, code using boost::ptr_map
 * iterators should use get_pointer(i) to get the pointer from
 * a boost::ptr_map iterator.
 *
 * Can be removed when we no longer support platforms on 1.33.
 *
 * @see http://www.boost.org/libs/ptr_container/doc/ptr_container.html#upgrading-from-boost-v-1-33
 */

#include <boost/type_traits/remove_const.hpp>

#if (BOOST_VERSION < 103400)

template <class PtrMapIter>
typename PtrMapIter::pointer get_pointer(const PtrMapIter& i)
{ return &*i; }

#else

template <class PtrMapIter>
typename boost::remove_const<typename PtrMapIter::value_type::second_type>::type
get_pointer(const PtrMapIter& i)
{ return i->second; }

#endif

}} // namespace qpid::ptr_map

#endif  /*!QPID_PTR_MAP*/
#ifndef QPID_PTR_MAP
#define QPID_PTR_MAP

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

#include <boost/ptr_container/ptr_map.hpp>
#include <boost/version.hpp>

namespace qpid {
namespace ptr_map {

/** @file
 * Workaround for API change between boost 1.33 and 1.34.
 *
 * To be portable across these versions, code using boost::ptr_map
 * iterators should use get_pointer(i) to get the pointer from
 * a boost::ptr_map iterator.
 *
 * Can be removed when we no longer support platforms on 1.33.
 *
 * @see http://www.boost.org/libs/ptr_container/doc/ptr_container.html#upgrading-from-boost-v-1-33
 */
#if (BOOST_VERSION < 103400)

template <class PtrMapIter>
typename PtrMapIter::pointer get_pointer(const PtrMapIter& i)
{ return &*i; }

#else

template <class PtrMapIter>
typename PtrMapIter::value_type::second_type get_pointer(const PtrMapIter& i)
{ return i->second; }

#endif

}} // namespace qpid::ptr_map

#endif  /*!QPID_PTR_MAP*/
