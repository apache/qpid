#ifndef QPID_AMQP_0_10_CODECS_H
#define QPID_AMQP_0_10_CODECS_H

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

#include "qpid/CommonImportExport.h"
#include "qpid/types/Variant.h"
#include "boost/shared_ptr.hpp"

namespace qpid {
namespace framing {
class FieldTable;
class FieldValue;
}
namespace amqp_0_10 {
/**
 * Codec for encoding/decoding a map of Variants using the AMQP 0-10
 * map encoding.
 */
class QPID_COMMON_CLASS_EXTERN MapCodec
{
  public:
    typedef qpid::types::Variant::Map ObjectType;
    static void   QPID_COMMON_EXTERN encode(const ObjectType&, std::string&);
    static void   QPID_COMMON_EXTERN decode(const std::string&, ObjectType&);
    static size_t QPID_COMMON_EXTERN encodedSize(const ObjectType&);
    static const  QPID_COMMON_EXTERN std::string contentType;
  private:
};

/**
 * Codec for encoding/decoding a list of Variants using the AMQP 0-10
 * list encoding.
 */
class QPID_COMMON_CLASS_EXTERN ListCodec
{
  public:
    typedef qpid::types::Variant::List ObjectType;
    static void   QPID_COMMON_EXTERN encode(const ObjectType&, std::string&);
    static void   QPID_COMMON_EXTERN decode(const std::string&, ObjectType&);
    static size_t QPID_COMMON_EXTERN encodedSize(const ObjectType&);
    static const  QPID_COMMON_EXTERN std::string contentType;
  private:
};

/**
 * @internal
 *
 * Conversion functions between qpid::types:Variant::Map and the
 * deprecated qpid::framing::FieldTable.
 *
 */
QPID_COMMON_EXTERN void translate(const qpid::types::Variant::Map& from,
                                  qpid::framing::FieldTable& to);
QPID_COMMON_EXTERN void translate(const qpid::types::Variant::Map& from, const std::string& efield, const qpid::types::Variant& evalue,
                                  qpid::framing::FieldTable& to);
QPID_COMMON_EXTERN void translate(const qpid::framing::FieldTable& from,
                                  qpid::types::Variant::Map& to);

QPID_COMMON_EXTERN void translate(const boost::shared_ptr<qpid::framing::FieldValue> from,
                                  qpid::types::Variant& to);
QPID_COMMON_EXTERN void translate(const types::Variant& from,
                                  boost::shared_ptr<qpid::framing::FieldValue> to);
QPID_COMMON_EXTERN boost::shared_ptr<qpid::framing::FieldValue> translate(const types::Variant& from);

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_CODECS_H*/
