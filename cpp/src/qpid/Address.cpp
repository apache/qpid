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

#include "qpid/Address.h"
#include "qpid/client/ConnectionSettings.h"

#include <ostream>

using namespace std;

namespace qpid {

const string Address::TCP("tcp");

ostream& operator<<(ostream& os, const Address& a) {
    // If the host is an IPv6 literal we need to print "[]" around it
    // (we detect IPv6 literals because they contain ":" which is otherwise illegal)
    if (a.host.find(':') != string::npos) {
        return os << a.protocol << ":[" << a.host << "]:" << a.port;
    } else {
        return os << a.protocol << ":" << a.host << ":" << a.port;
    }
}

bool operator==(const Address& x, const Address& y) {
    return y.protocol==x.protocol && y.host==x.host && y.port == x.port;
}
bool operator!=(const Address& x, const Address& y) { return !(x == y); }
} // namespace qpid
