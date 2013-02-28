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

#ifndef QPID_LEGACYSTORE_STOREEXCEPTION_H
#define QPID_LEGACYSTORE_STOREEXCEPTION_H

#include "qpid/legacystore/IdDbt.h"
#include <boost/format.hpp>

namespace mrg{
namespace msgstore{

class StoreException : public std::exception
{
    std::string text;
public:
    StoreException(const std::string& _text) : text(_text) {}
    StoreException(const std::string& _text, const DbException& cause) : text(_text + ": " + cause.what()) {}
    virtual ~StoreException() throw() {}
    virtual const char* what() const throw() { return text.c_str(); }
};

class StoreFullException : public StoreException
{
public:
    StoreFullException(const std::string& _text) : StoreException(_text) {}
    StoreFullException(const std::string& _text, const DbException& cause) : StoreException(_text, cause) {}
    virtual ~StoreFullException() throw() {}

};

#define THROW_STORE_EXCEPTION(MESSAGE) throw StoreException(boost::str(boost::format("%s (%s:%d)") % (MESSAGE) % __FILE__ % __LINE__))
#define THROW_STORE_EXCEPTION_2(MESSAGE, EXCEPTION) throw StoreException(boost::str(boost::format("%s (%s:%d)") % (MESSAGE) % __FILE__ % __LINE__), EXCEPTION)
#define THROW_STORE_FULL_EXCEPTION(MESSAGE) throw StoreFullException(boost::str(boost::format("%s (%s:%d)") % (MESSAGE) % __FILE__ % __LINE__))

}}

#endif // ifndef QPID_LEGACYSTORE_STOREEXCEPTION_H
