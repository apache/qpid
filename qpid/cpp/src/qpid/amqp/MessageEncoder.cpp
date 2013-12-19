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
#include "qpid/amqp/MessageEncoder.h"
#include "qpid/amqp/MapEncoder.h"
#include "qpid/amqp/MapSizeCalculator.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/log/Statement.h"
#include <assert.h>

namespace qpid {
namespace amqp {

namespace {
size_t optimisable(const MessageEncoder::Header& msg)
{
    if (msg.getDeliveryCount()) return 5;
    else if (msg.isFirstAcquirer()) return 4;
    else if (msg.hasTtl()) return 3;
    else if (msg.getPriority() != 4) return 2;
    else if (msg.isDurable()) return 1;
    else return 0;
}

size_t optimisable(const MessageEncoder::Properties& msg)
{
    if (msg.hasReplyToGroupId()) return 13;
    else if (msg.hasGroupSequence()) return 12;
    else if (msg.hasGroupId()) return 11;
    else if (msg.hasCreationTime()) return 10;
    else if (msg.hasAbsoluteExpiryTime()) return 9;
    else if (msg.hasContentEncoding()) return 8;
    else if (msg.hasContentType()) return 7;
    else if (msg.hasCorrelationId()) return 6;
    else if (msg.hasReplyTo()) return 5;
    else if (msg.hasSubject()) return 4;
    else if (msg.hasTo()) return 3;
    else if (msg.hasUserId()) return 2;
    else if (msg.hasMessageId()) return 1;
    else return 0;
}
size_t encodedSize(const std::string& s)
{
    size_t total = s.size();
    if (total > 255) total += 4;
    else total += 1;
    return total;
}
const std::string BINARY("binary");
}

void MessageEncoder::writeHeader(const Header& msg)
{
    size_t fields(optimise ? optimisable(msg) : 5);
    if (fields) {
        void* token = startList8(&qpid::amqp::message::HEADER);
        writeBoolean(msg.isDurable());
        if (fields > 1) writeUByte(msg.getPriority());

        if (msg.getTtl()) writeUInt(msg.getTtl());
        else if (fields > 2) writeNull();

        if (msg.isFirstAcquirer()) writeBoolean(true);
        else if (fields > 3) writeNull();

        if (msg.getDeliveryCount()) writeUInt(msg.getDeliveryCount());
        else if (fields > 4) writeNull();
        endList8(fields, token);
    }
}


void MessageEncoder::writeProperties(const Properties& msg)
{
    size_t fields(optimise ? optimisable(msg) : 13);
    if (fields) {
        void* token = startList32(&qpid::amqp::message::PROPERTIES);
        if (msg.hasMessageId()) writeString(msg.getMessageId());
        else writeNull();

        if (msg.hasUserId()) writeBinary(msg.getUserId());
        else if (fields > 1) writeNull();

        if (msg.hasTo()) writeString(msg.getTo());
        else if (fields > 2) writeNull();

        if (msg.hasSubject()) writeString(msg.getSubject());
        else if (fields > 3) writeNull();

        if (msg.hasReplyTo()) writeString(msg.getReplyTo());
        else if (fields > 4) writeNull();

        if (msg.hasCorrelationId()) writeString(msg.getCorrelationId());
        else if (fields > 5) writeNull();

        if (msg.hasContentType()) writeSymbol(msg.getContentType());
        else if (fields > 6) writeNull();

        if (msg.hasContentEncoding()) writeSymbol(msg.getContentEncoding());
        else if (fields > 7) writeNull();

        if (msg.hasAbsoluteExpiryTime()) writeTimestamp(msg.getAbsoluteExpiryTime());
        else if (fields > 8) writeNull();

        if (msg.hasCreationTime()) writeTimestamp(msg.getCreationTime());
        else if (fields > 9) writeNull();

        if (msg.hasGroupId()) writeString(msg.getGroupId());
        else if (fields > 10) writeNull();

        if (msg.hasGroupSequence()) writeUInt(msg.getGroupSequence());
        else if (fields > 11) writeNull();

        if (msg.hasReplyToGroupId()) writeString(msg.getReplyToGroupId());
        else if (fields > 12) writeNull();

        endList32(fields, token);
    }
}

void MessageEncoder::writeApplicationProperties(const ApplicationProperties& properties)
{
    MapSizeCalculator calc;
    properties.handle(calc);
    size_t required = calc.getTotalSizeRequired(&qpid::amqp::message::APPLICATION_PROPERTIES);
    assert(required <= getSize() - getPosition());
    MapEncoder encoder(skip(required), required);
    encoder.writeMetaData(calc.getSize(), calc.getCount()*2, &qpid::amqp::message::APPLICATION_PROPERTIES);
    properties.handle(encoder);
}

void MessageEncoder::writeApplicationProperties(const qpid::types::Variant::Map& properties)
{
    writeApplicationProperties(properties, !optimise || properties.size()*2 > 255 || getEncodedSizeForElements(properties) > 255);
}

void MessageEncoder::writeApplicationProperties(const qpid::types::Variant::Map& properties, bool large)
{
    writeMap(properties, &qpid::amqp::message::APPLICATION_PROPERTIES, large);
}

size_t MessageEncoder::getEncodedSize(const Header& h, const Properties& p, const qpid::types::Variant::Map& ap, const std::string& d)
{
    return getEncodedSize(h) + getEncodedSize(p, ap, d);
}

size_t MessageEncoder::getEncodedSize(const Header& h, const Properties& p, const ApplicationProperties& ap, const std::string& d)
{
    return getEncodedSize(h) + getEncodedSize(p) + getEncodedSize(ap) + getEncodedSizeForContent(d);
}

size_t MessageEncoder::getEncodedSize(const Properties& p, const qpid::types::Variant::Map& ap, const std::string& d)
{
    size_t total(getEncodedSize(p));
    //application-properties:
    total += 3/*descriptor*/ + getEncodedSize(ap, true);
    //body:
    if (d.size()) total += 3/*descriptor*/ + 1/*code*/ + encodedSize(d);

    return total;
}

size_t MessageEncoder::getEncodedSizeForContent(const std::string& d)
{
    if (d.size()) return 3/*descriptor*/ + 1/*code*/ + encodedSize(d);
    else return 0;
}

size_t MessageEncoder::getEncodedSize(const Header& h)
{
    //NOTE: this does not take optional optimisation into account,
    //i.e. it is a 'worst case' estimate for required buffer space
    size_t total(3/*descriptor*/ + 1/*code*/ + 1/*size*/ + 1/*count*/ + 5/*codes for each field*/);
    if (h.getPriority() != 4) total += 1;
    if (h.getDeliveryCount()) total += 4;
    if (h.hasTtl()) total += 4;
    return total;
}

size_t MessageEncoder::getEncodedSize(const Properties& p)
{
    //NOTE: this does not take optional optimisation into account,
    //i.e. it is a 'worst case' estimate for required buffer space
    size_t total(3/*descriptor*/ + 1/*code*/ + 4/*size*/ + 4/*count*/ + 13/*codes for each field*/);
    if (p.hasMessageId()) total += encodedSize(p.getMessageId());
    if (p.hasUserId()) total += encodedSize(p.getUserId());
    if (p.hasTo()) total += encodedSize(p.getTo());
    if (p.hasSubject()) total += encodedSize(p.getSubject());
    if (p.hasReplyTo()) total += encodedSize(p.getReplyTo());
    if (p.hasCorrelationId()) total += encodedSize(p.getCorrelationId());
    if (p.hasContentType()) total += encodedSize(p.getContentType());
    if (p.hasContentEncoding()) total += encodedSize(p.getContentEncoding());
    if (p.hasAbsoluteExpiryTime()) total += 8;
    if (p.hasCreationTime()) total += 8;
    if (p.hasGroupId()) total += encodedSize(p.getGroupId());
    if (p.hasGroupSequence()) total += 4;
    if (p.hasReplyToGroupId()) total += encodedSize(p.getReplyToGroupId());
    return total;
}

size_t MessageEncoder::getEncodedSize(const ApplicationProperties& p)
{
    MapSizeCalculator calc;
    p.handle(calc);
    return calc.getTotalSizeRequired(&qpid::amqp::message::APPLICATION_PROPERTIES);
}

size_t MessageEncoder::getEncodedSizeForElements(const qpid::types::Variant::Map& map)
{
    size_t total = 0;
    for (qpid::types::Variant::Map::const_iterator i = map.begin(); i != map.end(); ++i) {
        total += 1/*code*/ + encodedSize(i->first) + getEncodedSizeForValue(i->second);
    }
    return total;
}

size_t MessageEncoder::getEncodedSizeForValue(const qpid::types::Variant& value)
{
    size_t total = 0;
    switch (value.getType()) {
      case qpid::types::VAR_MAP:
        total += getEncodedSize(value.asMap(), true);
        break;
      case qpid::types::VAR_LIST:
        total += getEncodedSize(value.asList(), true);
        break;

      case qpid::types::VAR_VOID:
      case qpid::types::VAR_BOOL:
        total += 1;
        break;

      case qpid::types::VAR_UINT8:
      case qpid::types::VAR_INT8:
        total += 2;
        break;

      case qpid::types::VAR_UINT16:
      case qpid::types::VAR_INT16:
        total += 3;
        break;

      case qpid::types::VAR_UINT32:
      case qpid::types::VAR_INT32:
      case qpid::types::VAR_FLOAT:
        total += 5;
        break;

      case qpid::types::VAR_UINT64:
      case qpid::types::VAR_INT64:
      case qpid::types::VAR_DOUBLE:
        total += 9;
        break;

      case qpid::types::VAR_UUID:
        total += 17;
        break;

      case qpid::types::VAR_STRING:
        total += 1/*code*/ + encodedSize(value.getString());
        break;
    }
    return total;
}


size_t MessageEncoder::getEncodedSize(const qpid::types::Variant::Map& map, bool alwaysUseLargeMap)
{
    size_t total = getEncodedSizeForElements(map);

    //its not just the count that determines whether we can use a small map, but the aggregate size:
    if (alwaysUseLargeMap || map.size()*2 > 255 || total > 255) total +=  4/*size*/ + 4/*count*/;
    else total += 1/*size*/ + 1/*count*/;

    total += 1 /*code for map itself*/;

    return total;
}

size_t MessageEncoder::getEncodedSize(const qpid::types::Variant::List& list, bool alwaysUseLargeList)
{
    size_t total(0);
    for (qpid::types::Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
        total += getEncodedSizeForValue(*i);
    }

    //its not just the count that determines whether we can use a small list, but the aggregate size:
    if (alwaysUseLargeList || list.size()*2 > 255 || total > 255) total +=  4/*size*/ + 4/*count*/;
    else total += 1/*size*/ + 1/*count*/;

    total += 1 /*code for list itself*/;

    return total;
}
}} // namespace qpid::amqp
