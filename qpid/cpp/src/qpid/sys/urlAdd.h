#ifndef QPID_SYS_URLADD_H
#define QPID_SYS_URLADD_H

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

#include <qpid/Url.h>
#include <boost/bind.hpp>
#include <algorithm>

namespace qpid {
namespace sys {

/** Add addr to url if it is not already present. */
inline void urlAddAddress(Url& url, const Address& addr) {
    if (std::find(url.begin(), url.end(), addr) == url.end()) url.push_back(addr);
}

/** Add all addresses in more that are not already in url to url */
inline void urlAddUrl(Url& url, const Url& more) {
    for_each(more.begin(), more.end(), boost::bind(&urlAddAddress, boost::ref(url), _1));
}

/** Convert str to a Url and do urlAddUrl. */
inline void urlAddString(Url& url, const std::string& str, const std::string& defaultProtocol) {
    urlAddUrl(url, Url(str, defaultProtocol));
}

/** For each URL in a range, do urlAddUrl */
template <class UrlIterator>
void urlAddUrls(Url& url, UrlIterator i, UrlIterator j) {
    for_each(i, j, boost::bind(&urlAddUrl, boost::ref(url), _1));
}

/** For each string in a range, do urlAddUrl(Url(string)) */
template <class StringIterator>
void urlAddStrings(Url& url, StringIterator i, StringIterator j,
                   const std::string& defaultProtocol) {
    for_each(i, j, boost::bind(&urlAddString, boost::ref(url), _1, defaultProtocol));
}

}} // namespace qpid::sys

#endif  /*!QPID_SYS_URLADD_H*/
