#ifndef QPID_AMQP_MAPREADER_H
#define QPID_AMQP_MAPREADER_H

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
#include "CharSequence.h"
#include <string>

namespace qpid {
namespace amqp {

/**
 * Reading AMQP 1.0 encoded data which is constrained to be a symbol
 * keyeed map. The keys are assumed never to be described, the values
 * may be.
 */
class MapReader : public Reader
{
  public:
    virtual void onNullValue(const CharSequence& /*key*/, const Descriptor*) {}
    virtual void onBooleanValue(const CharSequence& /*key*/, bool, const Descriptor*) {}
    virtual void onUByteValue(const CharSequence& /*key*/, uint8_t, const Descriptor*) {}
    virtual void onUShortValue(const CharSequence& /*key*/, uint16_t, const Descriptor*) {}
    virtual void onUIntValue(const CharSequence& /*key*/, uint32_t, const Descriptor*) {}
    virtual void onULongValue(const CharSequence& /*key*/, uint64_t, const Descriptor*) {}
    virtual void onByteValue(const CharSequence& /*key*/, int8_t, const Descriptor*) {}
    virtual void onShortValue(const CharSequence& /*key*/, int16_t, const Descriptor*) {}
    virtual void onIntValue(const CharSequence& /*key*/, int32_t, const Descriptor*) {}
    virtual void onLongValue(const CharSequence& /*key*/, int64_t, const Descriptor*) {}
    virtual void onFloatValue(const CharSequence& /*key*/, float, const Descriptor*) {}
    virtual void onDoubleValue(const CharSequence& /*key*/, double, const Descriptor*) {}
    virtual void onUuidValue(const CharSequence& /*key*/, const CharSequence&, const Descriptor*) {}
    virtual void onTimestampValue(const CharSequence& /*key*/, int64_t, const Descriptor*) {}

    virtual void onBinaryValue(const CharSequence& /*key*/, const CharSequence&, const Descriptor*) {}
    virtual void onStringValue(const CharSequence& /*key*/, const CharSequence&, const Descriptor*) {}
    virtual void onSymbolValue(const CharSequence& /*key*/, const CharSequence&, const Descriptor*) {}

    /**
     * @return true to step into elements of the compound value, false
     * to skip over it
     */
    virtual bool onStartListValue(const CharSequence& /*key*/, uint32_t /*count*/, const Descriptor*) { return true; }
    virtual bool onStartMapValue(const CharSequence& /*key*/, uint32_t /*count*/, const Descriptor*) { return true; }
    virtual bool onStartArrayValue(const CharSequence& /*key*/, uint32_t /*count*/, const Constructor&, const Descriptor*) { return true; }
    virtual void onEndListValue(const CharSequence& /*key*/, uint32_t /*count*/, const Descriptor*) {}
    virtual void onEndMapValue(const CharSequence& /*key*/, uint32_t /*count*/, const Descriptor*) {}
    virtual void onEndArrayValue(const CharSequence& /*key*/, uint32_t /*count*/, const Descriptor*) {}


    //this class implements the Reader interface, thus acting as a transformer into a more map oriented scheme
    void onNull(const Descriptor*);
    void onBoolean(bool, const Descriptor*);
    void onUByte(uint8_t, const Descriptor*);
    void onUShort(uint16_t, const Descriptor*);
    void onUInt(uint32_t, const Descriptor*);
    void onULong(uint64_t, const Descriptor*);
    void onByte(int8_t, const Descriptor*);
    void onShort(int16_t, const Descriptor*);
    void onInt(int32_t, const Descriptor*);
    void onLong(int64_t, const Descriptor*);
    void onFloat(float, const Descriptor*);
    void onDouble(double, const Descriptor*);
    void onUuid(const CharSequence&, const Descriptor*);
    void onTimestamp(int64_t, const Descriptor*);

    void onBinary(const CharSequence&, const Descriptor*);
    void onString(const CharSequence&, const Descriptor*);
    void onSymbol(const CharSequence&, const Descriptor*);

    bool onStartList(uint32_t /*count*/, const CharSequence&, const Descriptor*);
    bool onStartMap(uint32_t /*count*/, const CharSequence&, const Descriptor*);
    bool onStartArray(uint32_t /*count*/, const CharSequence&, const Constructor&, const Descriptor*);
    void onEndList(uint32_t /*count*/, const Descriptor*);
    void onEndMap(uint32_t /*count*/, const Descriptor*);
    void onEndArray(uint32_t /*count*/, const Descriptor*);

    MapReader();
  private:
    CharSequence key;
    size_t level;
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_MAPREADER_H*/
