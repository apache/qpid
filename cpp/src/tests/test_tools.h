#ifndef TEST_TOOLS_H
#define TEST_TOOLS_H

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

#include <boost/test/test_tools.hpp>
#include <boost/assign/list_of.hpp>
#include <boost/regex.hpp>
#include <vector>

/** Stream operator so BOOST_CHECK_EQUALS works on vectors. */
namespace std {
template <class T>
ostream& operator <<(ostream& o, const vector<T>& v) {
    o << " {";
    typename vector<T>::const_iterator i = v.begin();
    if (i != v.end())
        o << *i++;
    while (i != v.end())
        o << ", " << *i++;
    return o << "}";
}
} // namespace std

/** NB: order of parameters is regex first, in line with
 * CHECK(expected, actual) convention.
 */
inline bool regexPredicate(const std::string& re, const std::string& text) {
    return boost::regex_match(text, boost::regex(re));
}

/** Check for regular expression match. You must #include <boost/regex.hpp> */
#define BOOST_CHECK_REGEX(re, text) \
    BOOST_CHECK_PREDICATE(regexPredicate, (re)(text))

/** Check if types of two objects (as given by typeinfo::name()) match. */
#define BOOST_CHECK_TYPEID_EQUAL(a,b) BOOST_CHECK_EQUAL(typeid(a).name(),typeid(b).name())

#endif  /*!TEST_TOOLS_H*/

