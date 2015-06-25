#ifndef QPID_BROKER_AMQP_DATAREADER_H
#define QPID_BROKER_AMQP_DATAREADER_H

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
#include "qpid/amqp/Reader.h"
#include <map>
#include <string>

struct pn_data_t;

namespace qpid {
namespace types {
class Variant;
}
namespace amqp {
struct Descriptor;
}
namespace broker {
namespace amqp {

/**
 * Allows use of Reader interface to read pn_data_t* data.
 */
class DataReader
{
  public:
    DataReader(qpid::amqp::Reader& reader);
    void read(pn_data_t*);
    static void read(pn_data_t*, std::map<std::string, qpid::types::Variant>&);
  private:
    qpid::amqp::Reader& reader;

    void readOne(pn_data_t*);
    void readMap(pn_data_t*, const qpid::amqp::Descriptor*);
    void readList(pn_data_t*, const qpid::amqp::Descriptor*);
    void readArray(pn_data_t*, const qpid::amqp::Descriptor*);
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_DATAREADER_H*/
