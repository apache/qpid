#ifndef QPID_BROKER_SESSIONOUTPUTEXCEPTION_H
#define QPID_BROKER_SESSIONOUTPUTEXCEPTION_H

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

#include "qpid/Exception.h"

namespace qpid {
namespace broker {

/**
 * This exception is used to signal 'session' exceptions (aka
 * execution exceptions in AMQP 0-10 terms) that occur during output
 * processing. It simply allows the channel of the session to be
 * specified in addition to the other details. Special treatment is
 * required at present because the output processing chain is
 * different from that which handles incoming commands (specifically
 * AggregateOutput cannot reasonably handle exceptions as it has no
 * context).
 */
struct SessionOutputException : qpid::SessionException
{
    const uint16_t channel;
    SessionOutputException(const qpid::SessionException& e, uint16_t c) : qpid::SessionException(e.code, e.getMessage()), channel(c) {}
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_SESSIONOUTPUTEXCEPTION_H*/
