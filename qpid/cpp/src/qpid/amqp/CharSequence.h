#ifndef QPID_AMQP_CHARSEQUENCE_H
#define QPID_AMQP_CHARSEQUENCE_H

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
#include <stddef.h>
#include <string>
#include "qpid/CommonImportExport.h"

namespace qpid {
namespace amqp {

/**
 * Simple record of a particular sequence of chars/bytes. The memroy
 * referenced is assumed to be owned by some other entity, this is
 * merely a pointer into a (segment of) it.
 */
struct CharSequence
{
    const char* data;
    size_t size;

    QPID_COMMON_EXTERN operator bool() const;
    QPID_COMMON_EXTERN std::string str() const;
    QPID_COMMON_EXTERN void init();

    QPID_COMMON_EXTERN static CharSequence create();
    QPID_COMMON_EXTERN static CharSequence create(const std::string& str);
    QPID_COMMON_EXTERN static CharSequence create(const char* data, size_t size);
    QPID_COMMON_EXTERN static CharSequence create(const unsigned char* data, size_t size);
};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_CHARSEQUENCE_H*/
