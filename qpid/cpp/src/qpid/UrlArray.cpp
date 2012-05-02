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
#include "UrlArray.h"

#include <qpid/framing/FieldValue.h>

namespace qpid {

std::vector<Url> urlArrayToVector(const framing::Array& array) {
    std::vector<Url> urls;
    for (framing::Array::ValueVector::const_iterator i = array.begin();
         i != array.end();
         ++i )
        urls.push_back(Url((*i)->get<std::string>()));
    return urls;
}

framing::Array vectorToUrlArray(const std::vector<Url>& urls) {
    framing::Array array(0x95);
    for (std::vector<Url>::const_iterator i = urls.begin(); i != urls.end(); ++i)
        array.add(boost::shared_ptr<framing::Str16Value>(new framing::Str16Value(i->str())));
    return array;
}

} // namespace qpid
