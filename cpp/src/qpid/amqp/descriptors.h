#ifndef QPID_AMQP_DESCRIPTORS_H
#define QPID_AMQP_DESCRIPTORS_H

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
#include "Descriptor.h"

namespace qpid {
namespace amqp {

namespace message {
const std::string HEADER_SYMBOL("amqp:header:list");
const std::string PROPERTIES_SYMBOL("amqp:properties:list");
const std::string DELIVERY_ANNOTATIONS_SYMBOL("amqp:delivery-annotations:map");
const std::string MESSAGE_ANNOTATIONS_SYMBOL("amqp:message-annotations:map");
const std::string APPLICATION_PROPERTIES_SYMBOL("amqp:application-properties:map");
const std::string AMQP_SEQUENCE_SYMBOL("amqp:amqp-sequence:list");
const std::string AMQP_VALUE_SYMBOL("amqp:amqp-sequence:*");
const std::string DATA_SYMBOL("amqp:data:binary");
const std::string FOOTER_SYMBOL("amqp:footer:map");

const uint64_t HEADER_CODE(0x70);
const uint64_t DELIVERY_ANNOTATIONS_CODE(0x71);
const uint64_t MESSAGE_ANNOTATIONS_CODE(0x72);
const uint64_t PROPERTIES_CODE(0x73);
const uint64_t APPLICATION_PROPERTIES_CODE(0x74);
const uint64_t DATA_CODE(0x75);
const uint64_t AMQP_SEQUENCE_CODE(0x76);
const uint64_t AMQP_VALUE_CODE(0x77);
const uint64_t FOOTER_CODE(0x78);

const Descriptor HEADER(HEADER_CODE);
const Descriptor DELIVERY_ANNOTATIONS(DELIVERY_ANNOTATIONS_CODE);
const Descriptor MESSAGE_ANNOTATIONS(MESSAGE_ANNOTATIONS_CODE);
const Descriptor PROPERTIES(PROPERTIES_CODE);
const Descriptor APPLICATION_PROPERTIES(APPLICATION_PROPERTIES_CODE);
const Descriptor DATA(DATA_CODE);
}

namespace sasl {
const std::string SASL_MECHANISMS_SYMBOL("amqp:sasl-mechanisms:list");
const std::string SASL_INIT_SYMBOL("amqp:sasl-init:list");
const std::string SASL_CHALLENGE_SYMBOL("amqp:sasl-challenge:list");
const std::string SASL_RESPONSE_SYMBOL("amqp:sasl-response:list");
const std::string SASL_OUTCOME_SYMBOL("amqp:sasl-outcome:list");

const uint64_t SASL_MECHANISMS_CODE(0x40);
const uint64_t SASL_INIT_CODE(0x41);
const uint64_t SASL_CHALLENGE_CODE(0x42);
const uint64_t SASL_RESPONSE_CODE(0x43);
const uint64_t SASL_OUTCOME_CODE(0x44);

const Descriptor SASL_MECHANISMS(SASL_MECHANISMS_CODE);
const Descriptor SASL_INIT(SASL_INIT_CODE);
const Descriptor SASL_CHALLENGE(SASL_CHALLENGE_CODE);
const Descriptor SASL_RESPONSE(SASL_RESPONSE_CODE);
const Descriptor SASL_OUTCOME(SASL_OUTCOME_CODE);
}

namespace filters {
const std::string LEGACY_DIRECT_FILTER_SYMBOL("apache.org:legacy-amqp-direct-binding:string");
const std::string LEGACY_TOPIC_FILTER_SYMBOL("apache.org:legacy-amqp-topic-binding:string");
const std::string QPID_SELECTOR_FILTER_SYMBOL("qpid.apache.org:selector-filter:string");

const uint64_t LEGACY_DIRECT_FILTER_CODE(0x0000468C00000000ULL);
const uint64_t LEGACY_TOPIC_FILTER_CODE(0x0000468C00000001ULL);
}

}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_DESCRIPTORS_H*/
