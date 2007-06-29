#ifndef AMQP_TYPES_H
#define AMQP_TYPES_H
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

/** \file
 * Type definitions and forward declarations of all types used to
 * in AMQP messages.
 */

#include <string>
#ifdef _WINDOWS
#include "windows.h"
typedef unsigned char  uint8_t;
typedef unsigned short uint16_t;
typedef unsigned int   uint32_t;
typedef unsigned __int64 uint64_t;
#endif
#ifndef _WINDOWS
#include <stdint.h>
#endif

namespace qpid {
namespace framing {

using std::string;
typedef uint16_t ChannelId;
typedef uint64_t RequestId;
typedef uint64_t ResponseId;
typedef uint32_t BatchOffset;
typedef uint16_t ClassId;
typedef uint16_t MethodId;
typedef uint16_t ReplyCode;

// Types represented by classes.
class Content;
class FieldTable;

// Useful constants

/** Maximum channel ID used by broker. Reserve high bit for internal use.*/
const ChannelId CHANNEL_MAX=(ChannelId(~1))>>1;
const ChannelId CHANNEL_HIGH_BIT= ChannelId(~CHANNEL_MAX);

}} // namespace qpid::framing
#endif
