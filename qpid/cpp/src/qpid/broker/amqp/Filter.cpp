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
#include "qpid/broker/amqp/Filter.h"
#include "qpid/broker/amqp/DataReader.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/log/Statement.h"
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace broker {
namespace amqp {

void Filter::read(pn_data_t* data)
{
    try {
        DataReader reader(*this);
        reader.read(data);
    } catch (const std::exception& e) {
        QPID_LOG(warning, "Error parsing filter: " << e.what());
    }
}

void Filter::write(pn_data_t* data)
{
    pn_data_put_map(data);
    pn_data_enter(data);
    subjectFilter.write(data);
    pn_data_exit(data);
}

void Filter::onStringValue(const qpid::amqp::CharSequence& key, const qpid::amqp::CharSequence& value, const qpid::amqp::Descriptor* descriptor)
{
    StringFilter filter;
    filter.key = std::string(key.data, key.size);
    filter.value = std::string(value.data, value.size);
    if (descriptor) {
        filter.described = true;
        filter.descriptor = *descriptor;
        if (descriptor->match(qpid::amqp::filters::LEGACY_TOPIC_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_TOPIC_FILTER_CODE)
            || descriptor->match(qpid::amqp::filters::LEGACY_DIRECT_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_DIRECT_FILTER_CODE)) {
            setSubjectFilter(filter);
        } else if (descriptor->match(qpid::amqp::filters::QPID_SELECTOR_FILTER_SYMBOL, 0)) {
            setSelectorFilter(filter);
        } else {
            QPID_LOG(notice, "Skipping unrecognised string filter with key " << filter.key << " and descriptor " << filter.descriptor);
        }
    } else {
        setSubjectFilter(filter);
    }
}

bool Filter::hasSubjectFilter() const
{
    return !subjectFilter.value.empty();
}

std::string Filter::getSubjectFilter() const
{
    return subjectFilter.value;
}


void Filter::setSubjectFilter(const StringFilter& filter)
{
    if (hasSubjectFilter()) {
        QPID_LOG(notice, "Skipping filter with key " << filter.key << "; subject filter already set");
    } else {
        subjectFilter = filter;
    }
}

bool Filter::hasSelectorFilter() const
{
    return !selectorFilter.value.empty();
}

std::string Filter::getSelectorFilter() const
{
    return selectorFilter.value;
}


void Filter::setSelectorFilter(const StringFilter& filter)
{
    if (hasSelectorFilter()) {
        QPID_LOG(notice, "Skipping filter with key " << filter.key << "; selector filter already set");
    } else {
        selectorFilter = filter;
    }
}

void Filter::bind(boost::shared_ptr<Exchange> exchange, boost::shared_ptr<Queue> queue)
{
    subjectFilter.bind(exchange, queue);
}

Filter::StringFilter::StringFilter() : described(false), descriptor(0) {}
namespace {
pn_bytes_t convert(const std::string& in)
{
    pn_bytes_t out;
    out.start = const_cast<char*>(in.data());
    out.size = in.size();
    return out;
}
pn_bytes_t convert(const qpid::amqp::CharSequence& in)
{
    pn_bytes_t out;
    out.start = const_cast<char*>(in.data);
    out.size = in.size;
    return out;
}
}
void Filter::StringFilter::write(pn_data_t* data)
{
    pn_data_put_symbol(data, convert(key));
    if (described) {
        pn_data_put_described(data);
        pn_data_enter(data);
        switch (descriptor.type) {
          case qpid::amqp::Descriptor::NUMERIC:
            pn_data_put_ulong(data, descriptor.value.code);
            break;
          case qpid::amqp::Descriptor::SYMBOLIC:
            pn_data_put_symbol(data, convert(descriptor.value.symbol));
            break;
        }
    }
    pn_data_put_string(data, convert(value));
    if (described) pn_data_exit(data);
}

void Filter::StringFilter::bind(boost::shared_ptr<Exchange> exchange, boost::shared_ptr<Queue> queue)
{
    if (described && exchange->getType() == DirectExchange::typeName
        && descriptor.match(qpid::amqp::filters::LEGACY_TOPIC_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_TOPIC_FILTER_CODE)) {
        QPID_LOG(info, "Using legacy topic filter as a direct matching filter for " << exchange->getName());
        if (descriptor.type == qpid::amqp::Descriptor::NUMERIC) {
            descriptor = qpid::amqp::Descriptor(qpid::amqp::filters::LEGACY_DIRECT_FILTER_CODE);
        } else {
            qpid::amqp::CharSequence symbol;
            symbol.data = qpid::amqp::filters::LEGACY_DIRECT_FILTER_SYMBOL.data();
            symbol.size = qpid::amqp::filters::LEGACY_DIRECT_FILTER_SYMBOL.size();
            descriptor = qpid::amqp::Descriptor(symbol);
        }
    }
    exchange->bind(queue, value, 0);
}

}}} // namespace qpid::broker::amqp
