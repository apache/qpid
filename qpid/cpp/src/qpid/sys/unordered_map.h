#ifndef _sys_unordered_map_h
#define _sys_unordered_map_h

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

// unordered_map include path is platform specific

#if defined(_MSC_VER) || defined(_LIBCPP_VERSION) || __cplusplus >= 201103L
#  include <unordered_map>
#elif defined(__SUNPRO_CC) || defined(__IBMCPP__)
#  include <boost/tr1/unordered_map.hpp>
#else
#  include <tr1/unordered_map>
#endif /* _MSC_VER */
namespace qpid {
namespace sys {
#if defined(_LIBCPP_VERSION) || __cplusplus >= 201103L
    using std::unordered_map;
#else
    using std::tr1::unordered_map;
#endif
}}


#endif /* _sys_unordered_map_h */
