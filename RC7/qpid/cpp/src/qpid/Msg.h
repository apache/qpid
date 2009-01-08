#ifndef QPID_MSG_H
#define QPID_MSG_H

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

#include <sstream>
#include <iostream>

namespace qpid {

/** A simple wrapper for std::ostringstream that allows
 * in place construction of a message and automatic conversion
 * to string.
 * E.g.
 *@code
 * void foo(const std::string&);
 * foo(Msg() << "hello " << 32);
 *@endcode
 * Will construct the string "hello 32" and pass it to foo()
 */
struct Msg {
    std::ostringstream os;
    Msg() {}
    Msg(const Msg& m) : os(m.str()) {}
    std::string str() const { return os.str(); }
    operator std::string() const { return str(); }
};

template <class T> const Msg& operator<<(const Msg& m, const T& t) {
    const_cast<std::ostringstream&>(m.os)<<t; return m;
}

inline std::ostream& operator<<(std::ostream& o, const Msg& m) {
    return o<<m.str();
}

/** Construct a message using operator << and append (file:line) */
#define QPID_MSG(message) ::qpid::Msg() << message << " (" << __FILE__ << ":" << __LINE__ << ")"

} // namespace qpid

#endif  /*!QPID_MSG_H*/
