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
#include "Unit.h"
#include "Codec.h"

namespace qpid {
namespace amqp_0_10 {

void Unit::setVariant() {
    switch (header.getType()) {
      case CONTROL: variant=ControlHolder(); break;
      case COMMAND: variant=CommandHolder();
      case HEADER: variant=Header();
      case BODY: variant=Body(header.getDataSize());
    }
}

struct GetTypeVisitor : public boost::static_visitor<SegmentType> {
    SegmentType operator()(const CommandHolder& ) const { return COMMAND; }
    SegmentType operator()(const ControlHolder& ) const { return CONTROL; }
    SegmentType operator()(const Header& ) const { return HEADER; }
    SegmentType operator()(const Body&) const { return BODY; }
};

void Unit::setHeader(uint8_t flags) {
    header.setFlags(flags);
    GetTypeVisitor getter;
    header.setType(variant.apply_visitor(getter));
    header.setDataSize(Codec::size(*this));
    // track automatically set from type.
    // no channel specified at this point.
}

std::ostream& operator<<(std::ostream& o, const Unit& u) {
    return o << u.getHeader() << " " << u.getVariant();
}

}} // namespace qpid::amqp_0_10
