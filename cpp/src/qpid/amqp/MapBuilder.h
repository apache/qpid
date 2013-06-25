#ifndef QPID_AMQP_MAPBUILDER_H
#define QPID_AMQP_MAPBUILDER_H

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
#include "MapReader.h"
#include "qpid/types/Variant.h"

namespace qpid {
namespace amqp {

/**
 * Utility to build a Variant::Map from a data stream (doesn't handle
 * nested maps or lists yet)
 */
class MapBuilder : public MapReader
{
  public:
    void onNullValue(const CharSequence& /*key*/, const Descriptor*);
    void onBooleanValue(const CharSequence& /*key*/, bool, const Descriptor*);
    void onUByteValue(const CharSequence& /*key*/, uint8_t, const Descriptor*);
    void onUShortValue(const CharSequence& /*key*/, uint16_t, const Descriptor*);
    void onUIntValue(const CharSequence& /*key*/, uint32_t, const Descriptor*);
    void onULongValue(const CharSequence& /*key*/, uint64_t, const Descriptor*);
    void onByteValue(const CharSequence& /*key*/, int8_t, const Descriptor*);
    void onShortValue(const CharSequence& /*key*/, int16_t, const Descriptor*);
    void onIntValue(const CharSequence& /*key*/, int32_t, const Descriptor*);
    void onLongValue(const CharSequence& /*key*/, int64_t, const Descriptor*);
    void onFloatValue(const CharSequence& /*key*/, float, const Descriptor*);
    void onDoubleValue(const CharSequence& /*key*/, double, const Descriptor*);
    void onUuidValue(const CharSequence& /*key*/, const CharSequence&, const Descriptor*);
    void onTimestampValue(const CharSequence& /*key*/, int64_t, const Descriptor*);

    void onBinaryValue(const CharSequence& /*key*/, const CharSequence&, const Descriptor*);
    void onStringValue(const CharSequence& /*key*/, const CharSequence&, const Descriptor*);
    void onSymbolValue(const CharSequence& /*key*/, const CharSequence&, const Descriptor*);

    qpid::types::Variant::Map getMap();
    const qpid::types::Variant::Map getMap() const;
  private:
    qpid::types::Variant::Map map;
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_MAPBUILDER_H*/
