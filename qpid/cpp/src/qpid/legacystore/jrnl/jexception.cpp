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

/**
 * \file jexception.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * Generic journal exception class mrg::journal::jexception. See comments
 * in file jexception.h for details.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/jexception.h"

#include <iomanip>
#include <sstream>
#include "qpid/legacystore/jrnl/jerrno.h"

#define CATLEN(p) MAX_MSG_SIZE - std::strlen(p) - 1

namespace mrg
{
namespace journal
{

jexception::jexception() throw ():
        std::exception(),
        _err_code(0)
{
    format();
}

jexception::jexception(const u_int32_t err_code) throw ():
        std::exception(),
        _err_code(err_code)
{
    format();
}

jexception::jexception(const char* additional_info) throw ():
        std::exception(),
        _err_code(0),
        _additional_info(additional_info)
{
    format();
}

jexception::jexception(const std::string& additional_info) throw ():
        std::exception(),
        _err_code(0),
        _additional_info(additional_info)
{
    format();
}

jexception::jexception(const u_int32_t err_code, const char* additional_info) throw ():
        std::exception(),
        _err_code(err_code),
        _additional_info(additional_info)
{
    format();
}

jexception::jexception(const u_int32_t err_code, const std::string& additional_info) throw ():
        std::exception(),
        _err_code(err_code),
        _additional_info(additional_info)
{
    format();
}

jexception::jexception(const u_int32_t err_code, const char* throwing_class,
        const char* throwing_fn) throw ():
        std::exception(),
        _err_code(err_code),
        _throwing_class(throwing_class),
        _throwing_fn(throwing_fn)
{
    format();
}

jexception::jexception(const u_int32_t err_code, const std::string& throwing_class,
        const std::string& throwing_fn) throw ():
        std::exception(),
        _err_code(err_code),
        _throwing_class(throwing_class),
        _throwing_fn(throwing_fn)
{
    format();
}

jexception::jexception(const u_int32_t err_code, const char* additional_info,
        const char* throwing_class, const char* throwing_fn) throw ():
        std::exception(),
        _err_code(err_code),
        _additional_info(additional_info),
        _throwing_class(throwing_class),
        _throwing_fn(throwing_fn)
{
    format();
}

jexception::jexception(const u_int32_t err_code, const std::string& additional_info,
        const std::string& throwing_class, const std::string& throwing_fn) throw ():
        std::exception(),
        _err_code(err_code),
        _additional_info(additional_info),
        _throwing_class(throwing_class),
        _throwing_fn(throwing_fn)
{
    format();
}

jexception::~jexception() throw ()
{}

void
jexception::format()
{
    const bool ai = !_additional_info.empty();
    const bool tc = !_throwing_class.empty();
    const bool tf = !_throwing_fn.empty();
    std::ostringstream oss;
    oss << "jexception 0x" << std::hex << std::setfill('0') << std::setw(4) << _err_code << " ";
    if (tc)
    {
        oss << _throwing_class;
        if (tf)
            oss << "::";
        else
            oss << " ";
    }
    if (tf)
        oss << _throwing_fn << "() ";
    if (tc || tf)
        oss << "threw " << jerrno::err_msg(_err_code);
    if (ai)
        oss << " (" << _additional_info << ")";
    _what.assign(oss.str());
}

const char*
jexception::what() const throw ()
{
    return _what.c_str();
}

std::ostream&
operator<<(std::ostream& os, const jexception& je)
{
    os << je.what();
    return os;
}

std::ostream&
operator<<(std::ostream& os, const jexception* jePtr)
{
    os << jePtr->what();
    return os;
}

} // namespace journal
} // namespace mrg
