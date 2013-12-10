#ifndef QPID_AMQP_DATABUILDER_H
#define QPID_AMQP_DATABUILDER_H

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
#include "Reader.h"
#include "qpid/types/Variant.h"
#include "qpid/CommonImportExport.h"
#include <stack>

namespace qpid {
namespace amqp {

/**
 * Utility to build a Variant based structure (or value) from a data stream
 */
class DataBuilder : public Reader
{
  public:
    QPID_COMMON_EXTERN DataBuilder(qpid::types::Variant);
    QPID_COMMON_EXTERN virtual ~DataBuilder();
    QPID_COMMON_EXTERN void onNull(const Descriptor*);
    QPID_COMMON_EXTERN void onBoolean(bool, const Descriptor*);
    QPID_COMMON_EXTERN void onUByte(uint8_t, const Descriptor*);
    QPID_COMMON_EXTERN void onUShort(uint16_t, const Descriptor*);
    QPID_COMMON_EXTERN void onUInt(uint32_t, const Descriptor*);
    QPID_COMMON_EXTERN void onULong(uint64_t, const Descriptor*);
    QPID_COMMON_EXTERN void onByte(int8_t, const Descriptor*);
    QPID_COMMON_EXTERN void onShort(int16_t, const Descriptor*);
    QPID_COMMON_EXTERN void onInt(int32_t, const Descriptor*);
    QPID_COMMON_EXTERN void onLong(int64_t, const Descriptor*);
    QPID_COMMON_EXTERN void onFloat(float, const Descriptor*);
    QPID_COMMON_EXTERN void onDouble(double, const Descriptor*);
    QPID_COMMON_EXTERN void onUuid(const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN void onTimestamp(int64_t, const Descriptor*);

    QPID_COMMON_EXTERN void onBinary(const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN void onString(const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN void onSymbol(const CharSequence&, const Descriptor*);

    QPID_COMMON_EXTERN bool onStartList(uint32_t /*count*/, const CharSequence&, const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN bool onStartMap(uint32_t /*count*/, const CharSequence&, const CharSequence&, const Descriptor*);
    QPID_COMMON_EXTERN bool onStartArray(uint32_t /*count*/, const CharSequence&, const Constructor&, const Descriptor*);
    QPID_COMMON_EXTERN void onEndList(uint32_t /*count*/, const Descriptor*);
    QPID_COMMON_EXTERN void onEndMap(uint32_t /*count*/, const Descriptor*);
    QPID_COMMON_EXTERN void onEndArray(uint32_t /*count*/, const Descriptor*);

    QPID_COMMON_EXTERN bool proceed();
    QPID_COMMON_EXTERN qpid::types::Variant& getValue();
  private:
    qpid::types::Variant base;
    std::stack<qpid::types::Variant*> nested;
    std::string key;

    void handle(const qpid::types::Variant& v);
    bool nest(const qpid::types::Variant& v);
    void onString(const std::string&, const std::string&);
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_DATABUILDER_H*/
