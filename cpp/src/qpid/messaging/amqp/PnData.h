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
 *  Helper class to read/write messaging types to/from pn_data_t.
 */
class PnData
{
  public:
    PnData(pn_data_t* d) : data(d) {}

    void write(const types::Variant& value);
    void write(const types::Variant::Map& map);
    void write(const types::Variant::List& list);

    bool read(pn_type_t type, types::Variant& value);
    bool read(types::Variant& value);
    void readList(types::Variant::List& value);
    void readMap(types::Variant::Map& value);
    void readArray(types::Variant::List& value);

    static pn_bytes_t str(const std::string&);
    static std::string str(const pn_bytes_t&);

  private:
    pn_data_t* data;
};
}}} // namespace messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_PNDATA_H*/
