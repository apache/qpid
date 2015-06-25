#ifndef QPID_AMQP_MAPHANDLER_H
#define QPID_AMQP_MAPHANDLER_H

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

namespace qpid {
namespace amqp {
struct CharSequence;
/**
 * Interface for processing entries in some map-like object
 */
class MapHandler
{
  public:
    virtual ~MapHandler() {}
    virtual void handleVoid(const CharSequence& key) = 0;
    virtual void handleBool(const CharSequence& key, bool value) = 0;
    virtual void handleUint8(const CharSequence& key, uint8_t value) = 0;
    virtual void handleUint16(const CharSequence& key, uint16_t value) = 0;
    virtual void handleUint32(const CharSequence& key, uint32_t value) = 0;
    virtual void handleUint64(const CharSequence& key, uint64_t value) = 0;
    virtual void handleInt8(const CharSequence& key, int8_t value) = 0;
    virtual void handleInt16(const CharSequence& key, int16_t value) = 0;
    virtual void handleInt32(const CharSequence& key, int32_t value) = 0;
    virtual void handleInt64(const CharSequence& key, int64_t value) = 0;
    virtual void handleFloat(const CharSequence& key, float value) = 0;
    virtual void handleDouble(const CharSequence& key, double value) = 0;
    virtual void handleString(const CharSequence& key, const CharSequence& value, const CharSequence& encoding) = 0;
  private:
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_MAPHANDLER_H*/
