#ifndef QPID_AMQP_DESCRIPTOR_H
#define QPID_AMQP_DESCRIPTOR_H

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
#include "qpid/amqp/CharSequence.h"
#include "qpid/sys/IntegerTypes.h"
#include <ostream>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace amqp {

/**
 * Representation of an AMQP 1.0 type descriptor.
 */
struct Descriptor
{
    union {
        CharSequence symbol;
        uint64_t code;
    } value;
    enum {
        NUMERIC,
        SYMBOLIC
    } type;
    boost::shared_ptr<Descriptor> nested;

    QPID_COMMON_EXTERN Descriptor(uint64_t code);
    QPID_COMMON_EXTERN Descriptor(const CharSequence& symbol);
    QPID_COMMON_EXTERN bool match(const std::string&, uint64_t) const;
    QPID_COMMON_EXTERN size_t getSize() const;
    QPID_COMMON_EXTERN Descriptor* nest(const Descriptor& d);
    QPID_COMMON_EXTERN std::string symbol() const;
    QPID_COMMON_EXTERN uint64_t code() const;
};

QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream& os, const Descriptor& d);

}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_DESCRIPTOR_H*/
