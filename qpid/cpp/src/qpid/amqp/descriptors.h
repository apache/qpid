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

// NOTE: If you add descriptor symbols and codes here, you must also update the DescriptorMap
// constructor in Descriptor.cpp.

namespace message {
const std::string HEADER_SYMBOL("amqp:header:list");
const std::string PROPERTIES_SYMBOL("amqp:properties:list");
const std::string DELIVERY_ANNOTATIONS_SYMBOL("amqp:delivery-annotations:map");
const std::string MESSAGE_ANNOTATIONS_SYMBOL("amqp:message-annotations:map");
const std::string APPLICATION_PROPERTIES_SYMBOL("amqp:application-properties:map");
const std::string AMQP_SEQUENCE_SYMBOL("amqp:amqp-sequence:list");
const std::string AMQP_VALUE_SYMBOL("amqp:amqp-value:*");
const std::string DATA_SYMBOL("amqp:data:binary");
const std::string FOOTER_SYMBOL("amqp:footer:map");
const std::string ACCEPTED_SYMBOL("amqp:accepted:list");

const uint64_t HEADER_CODE(0x70);
const uint64_t DELIVERY_ANNOTATIONS_CODE(0x71);
const uint64_t MESSAGE_ANNOTATIONS_CODE(0x72);
const uint64_t PROPERTIES_CODE(0x73);
const uint64_t APPLICATION_PROPERTIES_CODE(0x74);
const uint64_t DATA_CODE(0x75);
const uint64_t AMQP_SEQUENCE_CODE(0x76);
const uint64_t AMQP_VALUE_CODE(0x77);
const uint64_t FOOTER_CODE(0x78);
const uint64_t ACCEPTED_CODE(0x24);

const Descriptor HEADER(HEADER_CODE);
const Descriptor DELIVERY_ANNOTATIONS(DELIVERY_ANNOTATIONS_CODE);
const Descriptor MESSAGE_ANNOTATIONS(MESSAGE_ANNOTATIONS_CODE);
const Descriptor PROPERTIES(PROPERTIES_CODE);
const Descriptor APPLICATION_PROPERTIES(APPLICATION_PROPERTIES_CODE);
const Descriptor AMQP_VALUE(AMQP_VALUE_CODE);
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
const std::string LEGACY_HEADERS_FILTER_SYMBOL("apache.org:legacy-amqp-headers-binding:map");
const std::string NO_LOCAL_FILTER_SYMBOL("apache.org:no-local-filter:list");
const std::string SELECTOR_FILTER_SYMBOL("apache.org:selector-filter:string");
const std::string XQUERY_FILTER_SYMBOL("apache.org:xquery-filter:string");

const uint64_t LEGACY_DIRECT_FILTER_CODE(0x0000468C00000000ULL);
const uint64_t LEGACY_TOPIC_FILTER_CODE(0x0000468C00000001ULL);
const uint64_t LEGACY_HEADERS_FILTER_CODE(0x0000468C00000002ULL);
const uint64_t NO_LOCAL_FILTER_CODE(0x0000468C00000003ULL);
const uint64_t SELECTOR_FILTER_CODE(0x0000468C00000004ULL);
const uint64_t XQUERY_FILTER_CODE(0x0000468C00000005ULL);
}

namespace lifetime_policy {
const std::string DELETE_ON_CLOSE_SYMBOL("amqp:delete-on-close:list");
const std::string DELETE_ON_NO_LINKS_SYMBOL("amqp:delete-on-no-links:list");
const std::string DELETE_ON_NO_MESSAGES_SYMBOL("amqp:delete-on-no-messages:list");
const std::string DELETE_ON_NO_LINKS_OR_MESSAGES_SYMBOL("amqp:delete-on-no-links-or-messages:list");

const uint64_t DELETE_ON_CLOSE_CODE(0x2B);
const uint64_t DELETE_ON_NO_LINKS_CODE(0x2C);
const uint64_t DELETE_ON_NO_MESSAGES_CODE(0x2D);
const uint64_t DELETE_ON_NO_LINKS_OR_MESSAGES_CODE(0x2E);
}

namespace transaction {
const std::string DECLARE_SYMBOL("amqp:declare:list");
const std::string DISCHARGE_SYMBOL("amqp:discharge:list");
const std::string DECLARED_SYMBOL("amqp:declared:list");
const std::string TRANSACTIONAL_STATE_SYMBOL("amqp:transactional-state:list");

const uint64_t DECLARE_CODE(0x31);
const uint64_t DISCHARGE_CODE(0x32);
const uint64_t DECLARED_CODE(0x33);
const uint64_t TRANSACTIONAL_STATE_CODE(0x34);
}

namespace error_conditions {
//note these are not actually descriptors
const std::string INTERNAL_ERROR("amqp:internal-error");
const std::string NOT_FOUND("amqp:not-found");
const std::string UNAUTHORIZED_ACCESS("amqp:unauthorized-access");
const std::string DECODE_ERROR("amqp:decode-error");
const std::string NOT_ALLOWED("amqp:not-allowed");
const std::string NOT_IMPLEMENTED("amqp:not-implemented");
const std::string RESOURCE_LIMIT_EXCEEDED("amqp:resource-limit-exceeded");
const std::string RESOURCE_DELETED("amqp:resource-deleted");
const std::string PRECONDITION_FAILED("amqp:precondition-failed");
namespace transaction {
const std::string UNKNOWN_ID("amqp:transaction:unknown-id");
const std::string ROLLBACK("amqp:transaction:rollback");
}
}
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_DESCRIPTORS_H*/
