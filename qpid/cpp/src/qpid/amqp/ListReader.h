#ifndef QPID_AMQP_LISTREADER_H
#define QPID_AMQP_LISTREADER_H

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

namespace qpid {
namespace amqp {

/**
 * Utility to assist in reading AMQP encoded lists
 */
class ListReader : public Reader
{
  public:
    ListReader() : index(0), level(0) {}
    virtual ~ListReader() {}
    virtual void onNull(const Descriptor* descriptor) { getReader().onNull(descriptor); }
    virtual void onBoolean(bool v, const Descriptor* descriptor) { getReader().onBoolean(v, descriptor); }
    virtual void onUByte(uint8_t v, const Descriptor* descriptor) { getReader().onUByte(v, descriptor); }
    virtual void onUShort(uint16_t v, const Descriptor* descriptor) { getReader().onUShort(v, descriptor); }
    virtual void onUInt(uint32_t v, const Descriptor* descriptor) { getReader().onUInt(v, descriptor); }
    virtual void onULong(uint64_t v, const Descriptor* descriptor) { getReader().onULong(v, descriptor); }
    virtual void onByte(int8_t v, const Descriptor* descriptor) { getReader().onByte(v, descriptor); }
    virtual void onShort(int16_t v, const Descriptor* descriptor) { getReader().onShort(v, descriptor); }
    virtual void onInt(int32_t v, const Descriptor* descriptor) { getReader().onInt(v, descriptor); }
    virtual void onLong(int64_t v, const Descriptor* descriptor) { getReader().onLong(v, descriptor); }
    virtual void onFloat(float v, const Descriptor* descriptor) { getReader().onFloat(v, descriptor); }
    virtual void onDouble(double v, const Descriptor* descriptor) { getReader().onDouble(v, descriptor); }
    virtual void onUuid(const CharSequence& v, const Descriptor* descriptor) { getReader().onUuid(v, descriptor); }
    virtual void onTimestamp(int64_t v, const Descriptor* descriptor) { getReader().onTimestamp(v, descriptor); }

    virtual void onBinary(const CharSequence& v, const Descriptor* descriptor) { getReader().onBinary(v, descriptor); }
    virtual void onString(const CharSequence& v, const Descriptor* descriptor) { getReader().onString(v, descriptor); }
    virtual void onSymbol(const CharSequence& v, const Descriptor* descriptor) { getReader().onSymbol(v, descriptor); }

    virtual bool onStartList(uint32_t count, const CharSequence& elements, const CharSequence& all, const Descriptor* descriptor)
    {
        ++level;
        getReader().onStartList(count, elements, all, descriptor);
        return false;
    }
    virtual void onEndList(uint32_t count, const Descriptor* descriptor)
    {
        --level;
        getReader().onEndList(count, descriptor);
    }
    virtual bool onStartMap(uint32_t count, const CharSequence& elements, const CharSequence& all, const Descriptor* descriptor)
    {
        ++level;
        getReader().onStartMap(count, elements, all, descriptor);
        return false;
    }
    virtual void onEndMap(uint32_t count, const Descriptor* descriptor)
    {
        --level;
        getReader().onEndList(count, descriptor);
    }
    virtual bool onStartArray(uint32_t count, const CharSequence& v, const Constructor& c, const Descriptor* descriptor)
    {
        ++level;
        getReader().onStartArray(count, v, c, descriptor);
        return false;
    }
    virtual void onEndArray(uint32_t count, const Descriptor* descriptor)
    {
        --level;
        getReader().onEndList(count, descriptor);
    }
  private:
    size_t index;
    size_t level;
    Reader& getReader()
    {
        Reader& r = getReader(index);
        if (level == 0) ++index;
        return r;
    }
  protected:
    virtual Reader& getReader(size_t i) = 0;
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_LISTREADER_H*/
