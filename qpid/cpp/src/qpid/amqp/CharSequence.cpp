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
#include "CharSequence.h"

namespace qpid {
namespace amqp {

void CharSequence::init()
{
    data = 0;
    size = 0;
}

CharSequence::operator bool() const
{
    return data && size;
}
std::string CharSequence::str() const
{
    return (data && size) ? std::string(data, size) : std::string();
}

CharSequence CharSequence::create()
{
    CharSequence c = {0, 0};
    return c;
}

CharSequence CharSequence::create(const std::string& str)
{
    CharSequence c = {str.data(), str.size()};
    return c;
}

CharSequence CharSequence::create(const char* data, size_t size)
{
    CharSequence c = {data, size};
    return c;
}

CharSequence CharSequence::create(const unsigned char* data, size_t size)
{
    CharSequence c = {reinterpret_cast<const char*>(data), size};
    return c;
}

}} // namespace qpid::amqp
