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

#include "qpid/Msg.h"
#include <string>

namespace qpid {
using namespace std;

struct NonPrint { bool operator()(unsigned char c) { return !isprint(c) && !isspace(c); } };

const char hex[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

std::string quote(const std::string& str) {
    NonPrint nonPrint;
    size_t n = std::count_if(str.begin(), str.end(), nonPrint);
    if (n==0) return str;
    std::string ret;
    ret.reserve(str.size()+2*n); // Avoid extra allocations.
    for (string::const_iterator i = str.begin(); i != str.end(); ++i) {
        if (nonPrint(*i)) {
            ret.push_back('\\');
            ret.push_back('x');
            ret.push_back(hex[((*i) >> 4)&0xf]);
            ret.push_back(hex[(*i) & 0xf]);
        }
        else ret.push_back(*i);
    }
    return ret;
}

// Quote the string so messages with null characters are preserved, e.g. messages with XIDs */
std::string Msg::str() const {
    return quote(os.str());
}

} // namespace qpid
