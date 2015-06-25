#ifndef QPID_MESSAGING_AMQP_PNDATA_H
#define QPID_MESSAGING_AMQP_PNDATA_H

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

#include "qpid/types/Variant.h"
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace messaging {
namespace amqp {

/**
 *  Helper class to put/get messaging types to/from pn_data_t.
 */
class PnData
{
  public:
    pn_data_t* data;

    PnData(pn_data_t* d) : data(d) {}

    void put(const types::Variant& value);
    void put(const types::Variant::Map& map);
    void put(const types::Variant::List& list);
    void put(int32_t n) { pn_data_put_int(data, n); }
    void putSymbol(const std::string& symbol) { pn_data_put_symbol(data, bytes(symbol)); }

    bool get(pn_type_t type, types::Variant& value);
    bool get(types::Variant& value);
    void getList(types::Variant::List& value);
    void getMap(types::Variant::Map& value);
    void getArray(types::Variant::List& value);

    static pn_bytes_t bytes(const std::string&);
    static std::string string(const pn_bytes_t&);
};
}}} // namespace messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_PNDATA_H*/
