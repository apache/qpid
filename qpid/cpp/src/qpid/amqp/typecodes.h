#ifndef QPID_AMQP_TYPECODES_H
#define QPID_AMQP_TYPECODES_H

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
namespace qpid {
namespace amqp {

namespace typecodes
{
const uint8_t DESCRIPTOR(0x0);

const uint8_t NULL_VALUE(0x40);

const uint8_t BOOLEAN(0x56);
const uint8_t BOOLEAN_TRUE(0x41);
const uint8_t BOOLEAN_FALSE(0x42);

const uint8_t UBYTE(0x50);
const uint8_t USHORT(0x60);
const uint8_t UINT(0x70);
const uint8_t UINT_SMALL(0x52);
const uint8_t UINT_ZERO(0x43);
const uint8_t ULONG(0x80);
const uint8_t ULONG_SMALL(0x53);
const uint8_t ULONG_ZERO(0x44);

const uint8_t BYTE(0x51);
const uint8_t SHORT(0x61);
const uint8_t INT(0x71);
const uint8_t INT_SMALL(0x54);
const uint8_t LONG(0x81);
const uint8_t LONG_SMALL(0x55);

const uint8_t FLOAT(0x72);
const uint8_t DOUBLE(0x82);

const uint8_t DECIMAL32(0x74);
const uint8_t DECIMAL64(0x84);
const uint8_t DECIMAL128(0x94);

const uint8_t CHAR_UTF32(0x73);
const uint8_t TIMESTAMP(0x83);
const uint8_t UUID(0x98);

const uint8_t BINARY8(0xa0);
const uint8_t BINARY32(0xb0);
const uint8_t STRING8(0xa1);
const uint8_t STRING32(0xb1);
const uint8_t SYMBOL8(0xa3);
const uint8_t SYMBOL32(0xb3);

typedef std::pair<uint8_t, uint8_t> CodePair;
const CodePair SYMBOL(SYMBOL8, SYMBOL32);
const CodePair STRING(STRING8, STRING32);
const CodePair BINARY(BINARY8, BINARY32);

const uint8_t LIST0(0x45);
const uint8_t LIST8(0xc0);
const uint8_t LIST32(0xd0);
const uint8_t MAP8(0xc1);
const uint8_t MAP32(0xd1);
const uint8_t ARRAY8(0xe0);
const uint8_t ARRAY32(0xf0);


const std::string NULL_NAME("null");
const std::string BOOLEAN_NAME("bool");

const std::string UBYTE_NAME("ubyte");
const std::string USHORT_NAME("ushort");
const std::string UINT_NAME("uint");
const std::string ULONG_NAME("ulong");

const std::string BYTE_NAME("byte");
const std::string SHORT_NAME("short");
const std::string INT_NAME("int");
const std::string LONG_NAME("long");

const std::string FLOAT_NAME("float");
const std::string DOUBLE_NAME("double");

const std::string TIMESTAMP_NAME("timestamp");
const std::string UUID_NAME("uuid");

const std::string BINARY_NAME("binary");
const std::string STRING_NAME("string");
const std::string SYMBOL_NAME("symbol");

const std::string LIST_NAME("list");
const std::string MAP_NAME("map");
const std::string ARRAY_NAME("array");
}

}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_TYPECODES_H*/
