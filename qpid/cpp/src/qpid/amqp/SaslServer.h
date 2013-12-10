#ifndef QPID_AMQP_SASLSERVER_H
#define QPID_AMQP_SASLSERVER_H

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
#include "qpid/amqp/Sasl.h"

namespace qpid {
namespace amqp {

/**
 * Utility for decoding and encoding SASL frames by the peer acting as
 * the SASL server.
 */
class SaslServer : public Sasl
{
  public:
    QPID_COMMON_EXTERN SaslServer(const std::string& id);
    QPID_COMMON_EXTERN virtual ~SaslServer();
    virtual void init(const std::string& mechanism, const std::string* response, const std::string* hostname) = 0;
    virtual void response(const std::string*) = 0;

    QPID_COMMON_EXTERN void mechanisms(const std::string& mechanisms);
    QPID_COMMON_EXTERN void challenge(const std::string*);
    QPID_COMMON_EXTERN void completed(bool succeeded);

  private:
    QPID_COMMON_EXTERN bool onStartList(uint32_t count, const CharSequence& arguments, const CharSequence&, const Descriptor* descriptor);
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_SASLSERVER_H*/
