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
#include "qpid/amqp/MapReader.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace amqp {

void MapReader::onNull(const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onNullValue(key, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}
void MapReader::onBoolean(bool v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onBooleanValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onUByte(uint8_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onUByteValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onUShort(uint16_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onUShortValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onUInt(uint32_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onUIntValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onULong(uint64_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onULongValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onByte(int8_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onByteValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onShort(int16_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onShortValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onInt(int32_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onIntValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onLong(int64_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onLongValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onFloat(float v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onFloatValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onDouble(double v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onDoubleValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onUuid(const CharSequence& v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onUuidValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onTimestamp(int64_t v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onTimestampValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onBinary(const CharSequence& v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onBinaryValue(key, v, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onString(const CharSequence& v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onStringValue(key, v, d);
        clearKey();
    } else {
        if (keyType & STRING_KEY) {
            key = v;
        } else {
            throw qpid::Exception(QPID_MSG("Expecting symbol as key, got string " << v.str()));
        }
    }
}

void MapReader::onSymbol(const CharSequence& v, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onSymbolValue(key, v, d);
        clearKey();
    } else {
        if (keyType & SYMBOL_KEY) {
            key = v;
        } else {
            throw qpid::Exception(QPID_MSG("Expecting string as key, got symbol " << v.str()));
        }
    }
}

bool MapReader::onStartList(uint32_t count, const CharSequence&, const CharSequence&, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        bool step = onStartListValue(key, count, d);
        clearKey();
        return step;
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
    return true;
}

bool MapReader::onStartMap(uint32_t count, const CharSequence&, const CharSequence&, const Descriptor* d)
{
    if (level++) {
        if (key) {
            bool step = onStartMapValue(key, count, d);
            clearKey();
            return step;
        } else {
            throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
        }
    }
    return true;
}

bool MapReader::onStartArray(uint32_t count, const CharSequence&, const Constructor& c, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        bool step = onStartArrayValue(key, count, c, d);
        clearKey();
        return step;
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
    return true;
}

void MapReader::onEndList(uint32_t count, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onEndListValue(key, count, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

void MapReader::onEndMap(uint32_t count, const Descriptor* d)
{
    if (--level) {
        onEndMapValue(key, count, d);
        clearKey();
    }
}

void MapReader::onEndArray(uint32_t count, const Descriptor* d)
{
    if (!level) throw qpid::Exception(QPID_MSG("Expecting map as top level datum"));
    if (key) {
        onEndArrayValue(key, count, d);
        clearKey();
    } else {
        throw qpid::Exception(QPID_MSG("Expecting symbol as key"));
    }
}

MapReader::MapReader() : level(0), keyType(SYMBOL_KEY)
{
    clearKey();
}

void MapReader::setAllowedKeyType(int t)
{
    keyType = t;
}

void MapReader::clearKey()
{
    key.data = 0; key.size = 0;
}

const int MapReader::SYMBOL_KEY(1);
const int MapReader::STRING_KEY(2);
}} // namespace qpid::amqp
