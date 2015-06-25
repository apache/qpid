#ifndef QPID_AMQP_LOGGINGREADER_H
#define QPID_AMQP_LOGGINGREADER_H

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
#include "qpid/log/Statement.h"

namespace qpid {
namespace amqp {

class LoggingReader : public Reader
{
  public:
    virtual ~LoggingReader() {}
    virtual void onNull(const Descriptor*) { if (!ignoreNull()) QPID_LOG(warning, prefix() << "null" << suffix()); }
    virtual void onBoolean(bool, const Descriptor*) { QPID_LOG(warning, prefix() << "boolean" << suffix()); }
    virtual void onUByte(uint8_t, const Descriptor*) { QPID_LOG(warning, prefix() << "ubyte" << suffix()); }
    virtual void onUShort(uint16_t, const Descriptor*) { QPID_LOG(warning, prefix() << "ushort" << suffix()); }
    virtual void onUInt(uint32_t, const Descriptor*) { QPID_LOG(warning, prefix() << "uint" << suffix()); }
    virtual void onULong(uint64_t, const Descriptor*) { QPID_LOG(warning, prefix() << "ulong" << suffix()); }
    virtual void onByte(int8_t, const Descriptor*) { QPID_LOG(warning, prefix() << "byte" << suffix()); }
    virtual void onShort(int16_t, const Descriptor*) { QPID_LOG(warning, prefix() << "short" << suffix()); }
    virtual void onInt(int32_t, const Descriptor*) { QPID_LOG(warning, prefix() << "int" << suffix()); }
    virtual void onLong(int64_t, const Descriptor*) { QPID_LOG(warning, prefix() << "long" << suffix()); }
    virtual void onFloat(float, const Descriptor*) { QPID_LOG(warning, prefix() << "float" << suffix()); }
    virtual void onDouble(double, const Descriptor*) { QPID_LOG(warning, prefix() << "double" << suffix()); }
    virtual void onUuid(const CharSequence&, const Descriptor*) { QPID_LOG(warning, prefix() << "uuid" << suffix()); }
    virtual void onTimestamp(int64_t, const Descriptor*) { QPID_LOG(warning, prefix() << "timestamp" << suffix()); }

    virtual void onBinary(const CharSequence&, const Descriptor*) { QPID_LOG(warning, prefix() << "binary" << suffix()); }
    virtual void onString(const CharSequence&, const Descriptor*) { QPID_LOG(warning, prefix() << "string" << suffix()); }
    virtual void onSymbol(const CharSequence&, const Descriptor*) { QPID_LOG(warning, prefix() << "symbol" << suffix()); }

    virtual bool onStartList(uint32_t, const CharSequence&, const Descriptor*) { QPID_LOG(warning, prefix() << "list" << suffix()); return recursive; }
    virtual bool onStartMap(uint32_t, const CharSequence&, const Descriptor*) { QPID_LOG(warning, prefix() << "map" << suffix()); return recursive; }
    virtual bool onStartArray(uint32_t, const CharSequence&, const Constructor&, const Descriptor*) { QPID_LOG(warning, prefix() << "array" << suffix()); return recursive; }
  protected:
    virtual bool recursive() { return true; }
    virtual bool ignoreNull() { return true; }
    virtual std::string prefix() = 0;
    virtual std::string suffix() = 0;
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_LOGGINGREADER_H*/
