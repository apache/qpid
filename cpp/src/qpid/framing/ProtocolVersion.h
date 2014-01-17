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
#ifndef _ProtocolVersion_
#define _ProtocolVersion_

#include "qpid/framing/amqp_types.h"
#include "qpid/CommonImportExport.h"

#include <string>

namespace qpid
{
namespace framing
{

class QPID_COMMON_CLASS_EXTERN ProtocolVersion
{
private:
    uint8_t major_;
    uint8_t minor_;
    uint8_t protocol_;

public:
    explicit ProtocolVersion(uint8_t _major=0, uint8_t _minor=0, uint8_t _protocol=0)
        : major_(_major), minor_(_minor), protocol_(_protocol) {}

    QPID_COMMON_INLINE_EXTERN uint8_t getMajor() const { return major_; }
    QPID_COMMON_INLINE_EXTERN void setMajor(uint8_t major) { major_ = major; }
    QPID_COMMON_INLINE_EXTERN uint8_t getMinor() const { return minor_; }
    QPID_COMMON_INLINE_EXTERN void setMinor(uint8_t minor) { minor_ = minor; }
    QPID_COMMON_INLINE_EXTERN uint8_t getProtocol() const { return protocol_; }
    QPID_COMMON_INLINE_EXTERN void setProtocol(uint8_t protocol) { protocol_ = protocol; }
    QPID_COMMON_EXTERN const std::string toString() const;

    QPID_COMMON_EXTERN ProtocolVersion& operator=(ProtocolVersion p);

    QPID_COMMON_EXTERN bool operator==(ProtocolVersion p) const;
    QPID_COMMON_INLINE_EXTERN bool operator!=(ProtocolVersion p) const { return ! (*this == p); }
    QPID_COMMON_EXTERN static const uint8_t AMQP;
    QPID_COMMON_EXTERN static const uint8_t LEGACY_AMQP;
    QPID_COMMON_EXTERN static const uint8_t TLS;
    QPID_COMMON_EXTERN static const uint8_t SASL;
};

} // namespace framing
} // namespace qpid


#endif // ifndef _ProtocolVersion_
