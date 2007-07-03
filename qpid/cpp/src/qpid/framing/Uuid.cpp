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

#include "Uuid.h"
#include "uuid/uuid.h"

namespace qpid {
namespace framing {

using namespace std;

Uuid::Uuid() { uuid_generate(c_array()); }

Uuid::Uuid(uint8_t* uu) { uuid_copy(c_array(),uu); }

static const size_t UNPARSED_SIZE=36; 

ostream& operator<<(ostream& out, const Uuid& uuid) {
    char unparsed[UNPARSED_SIZE + 1];
    uuid_unparse(uuid.data(), unparsed);
    return out << unparsed;
}

istream& operator>>(istream& in, Uuid& uuid) {
    char unparsed[UNPARSED_SIZE + 1] = {0};
    in.get(unparsed, sizeof(unparsed));
    if (uuid_parse(unparsed, uuid.c_array()) != 0) 
        in.setstate(ios::failbit);
    return in;
}

}} // namespace qpid::framing
