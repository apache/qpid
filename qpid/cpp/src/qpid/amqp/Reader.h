#ifndef QPID_AMQP_READER_H
#define QPID_AMQP_READER_H

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
#include "qpid/sys/IntegerTypes.h"
#include <stddef.h>

namespace qpid {
namespace amqp {
struct CharSequence;
struct Constructor;
struct Descriptor;

/**
 * Allows an event-driven, callback-based approach to processing an
 * AMQP encoded data stream. By sublassing and implementing the
 * methods of interest, readers can be constructed for different
 * contexts.
 */
class Reader
{
  public:
    virtual ~Reader() {}
    virtual void onNull(const Descriptor*) {}
    virtual void onBoolean(bool, const Descriptor*) {}
    virtual void onUByte(uint8_t, const Descriptor*) {}
    virtual void onUShort(uint16_t, const Descriptor*) {}
    virtual void onUInt(uint32_t, const Descriptor*) {}
    virtual void onULong(uint64_t, const Descriptor*) {}
    virtual void onByte(int8_t, const Descriptor*) {}
    virtual void onShort(int16_t, const Descriptor*) {}
    virtual void onInt(int32_t, const Descriptor*) {}
    virtual void onLong(int64_t, const Descriptor*) {}
    virtual void onFloat(float, const Descriptor*) {}
    virtual void onDouble(double, const Descriptor*) {}
    virtual void onUuid(const CharSequence&, const Descriptor*) {}
    virtual void onTimestamp(int64_t, const Descriptor*) {}

    virtual void onBinary(const CharSequence&, const Descriptor*) {}
    virtual void onString(const CharSequence&, const Descriptor*) {}
    virtual void onSymbol(const CharSequence&, const Descriptor*) {}

    /**
     * @return true to get elements of the compound value, false
     * to skip over it
     */
    virtual bool onStartList(uint32_t /*count*/, const CharSequence& /*elements*/, const CharSequence& /*complete*/, const Descriptor*) { return true; }
    virtual bool onStartMap(uint32_t /*count*/, const CharSequence& /*elements*/, const CharSequence& /*complete*/, const Descriptor*) { return true; }
    virtual bool onStartArray(uint32_t /*count*/, const CharSequence&, const Constructor&, const Descriptor*) { return true; }
    virtual void onEndList(uint32_t /*count*/, const Descriptor*) {}
    virtual void onEndMap(uint32_t /*count*/, const Descriptor*) {}
    virtual void onEndArray(uint32_t /*count*/, const Descriptor*) {}

    virtual void onDescriptor(const Descriptor&, const char*) {}

    virtual bool proceed() { return true; }
  private:
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_READER_H*/
