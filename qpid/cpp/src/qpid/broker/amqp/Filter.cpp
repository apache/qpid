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
#include "qpid/broker/amqp/Authorise.h"
#include "qpid/broker/amqp/DataReader.h"
#include "qpid/broker/amqp/Outgoing.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/HeadersExchange.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/log/Statement.h"
extern "C" {
#include <proton/engine.h>
}

namespace qpid {
namespace broker {
namespace amqp {
namespace {
const std::string XQUERY("xquery");
const std::string XML("xml");
const std::string DEFAULT_SUBJECT_FILTER("default-subject-filter");
const std::string DEFAULT_HEADERS_FILTER("default-headers-filter");
const std::string XMATCH("x-match");
const std::string ALL("all");
const std::string DEFAULT_XQUERY_FILTER("default-xquery-filter");
const std::string DEFAULT_XQUERY_VALUE("true()");
const std::string WILDCARD("#");
}
Filter::Filter() : inHeadersMap(false), nolocal(false) {}

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
    if (!active.empty()) {
        pn_data_put_map(data);
        pn_data_enter(data);
        for (std::vector<FilterBase*>::const_iterator i = active.begin(); i != active.end(); ++i) {
            (*i)->write(data);
        }
        pn_data_exit(data);
    }
}

void Filter::onStringValue(const qpid::amqp::CharSequence& key, const qpid::amqp::CharSequence& value, const qpid::amqp::Descriptor* descriptor)
{
    if (inHeadersMap) {
        headersFilter.value[std::string(key.data, key.size)] = std::string(value.data, value.size);
    } else {
        StringFilter filter;
        filter.key = std::string(key.data, key.size);
        filter.value = std::string(value.data, value.size);
        if (descriptor) {
            filter.described = true;
            filter.descriptor = *descriptor;
            if (descriptor->match(qpid::amqp::filters::LEGACY_TOPIC_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_TOPIC_FILTER_CODE)
                || descriptor->match(qpid::amqp::filters::LEGACY_DIRECT_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_DIRECT_FILTER_CODE)) {
                setFilter(subjectFilter, filter);
            } else if (descriptor->match(qpid::amqp::filters::SELECTOR_FILTER_SYMBOL, qpid::amqp::filters::SELECTOR_FILTER_CODE)) {
                setFilter(selectorFilter, filter);
            } else if (descriptor->match(qpid::amqp::filters::XQUERY_FILTER_SYMBOL, qpid::amqp::filters::XQUERY_FILTER_CODE)) {
                setFilter(xqueryFilter, filter);
            } else if (descriptor && descriptor->match(qpid::amqp::filters::NO_LOCAL_FILTER_SYMBOL, qpid::amqp::filters::NO_LOCAL_FILTER_CODE)) {
                //though the no-local-filter is define to be a list, the JMS client sends it as a string
                nolocal = true;
            } else {
                QPID_LOG(notice, "Skipping unrecognised string filter with key " << filter.key << " and descriptor " << filter.descriptor);
            }
        } else {
            setFilter(subjectFilter, filter);
        }
    }
}
void Filter::onNullValue(const qpid::amqp::CharSequence& key, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = qpid::types::Variant();
}
void Filter::onBooleanValue(const qpid::amqp::CharSequence& key, bool value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onUByteValue(const qpid::amqp::CharSequence& key, uint8_t value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onUShortValue(const qpid::amqp::CharSequence& key, uint16_t value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onUIntValue(const qpid::amqp::CharSequence& key, uint32_t value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onULongValue(const qpid::amqp::CharSequence& key, uint64_t value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onByteValue(const qpid::amqp::CharSequence& key, int8_t value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onShortValue(const qpid::amqp::CharSequence& key, int16_t value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onIntValue(const qpid::amqp::CharSequence& key, int32_t value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onLongValue(const qpid::amqp::CharSequence& key, int64_t value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onFloatValue(const qpid::amqp::CharSequence& key, float value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

void Filter::onDoubleValue(const qpid::amqp::CharSequence& key, double value, const qpid::amqp::Descriptor*)
{
    headersFilter.value[std::string(key.data, key.size)] = value;
}

bool Filter::onStartListValue(const qpid::amqp::CharSequence& /*key*/, uint32_t /*count*/, const qpid::amqp::Descriptor* descriptor)
{
    if (descriptor && descriptor->match(qpid::amqp::filters::NO_LOCAL_FILTER_SYMBOL, qpid::amqp::filters::NO_LOCAL_FILTER_CODE)) {
        nolocal = true;
    }
    return false;
}

bool Filter::onStartMapValue(const qpid::amqp::CharSequence& key, uint32_t /*count*/, const qpid::amqp::Descriptor* descriptor)
{
    if (inHeadersMap) {
        QPID_LOG(warning, "Skipping illegal nested map data in headers filter");
        return false;
    } else if (descriptor && descriptor->match(qpid::amqp::filters::LEGACY_HEADERS_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_HEADERS_FILTER_CODE)) {
        inHeadersMap = true;
        setAllowedKeyType(STRING_KEY);
        headersFilter.requested = true;
        headersFilter.described = true;
        headersFilter.descriptor = *descriptor;
        headersFilter.key = std::string(key.data, key.size);
        return true;
    } else {
        if (descriptor) {
            QPID_LOG(info, "Skipping unrecognised map data in filter: " << *descriptor);
        } else {
            QPID_LOG(info, "Skipping undescribed map data in filter");
        }
        return false;
    }
}
void Filter::onEndMapValue(const qpid::amqp::CharSequence&, uint32_t, const qpid::amqp::Descriptor*)
{
    if (inHeadersMap) {
        inHeadersMap = false;
        setAllowedKeyType(SYMBOL_KEY);
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


bool Filter::hasSelectorFilter() const
{
    return !selectorFilter.value.empty();
}

std::string Filter::getSelectorFilter() const
{
    return selectorFilter.value;
}

void Filter::setFilter(StringFilter& lhs, const StringFilter& rhs)
{
    if (!lhs.value.empty()) {
        QPID_LOG(notice, "Skipping filter with key " << rhs.key << "; value provided for " << lhs.key << " already");
    } else {
        lhs = rhs;
        lhs.requested = true;
    }
}

void Filter::apply(boost::shared_ptr<Outgoing> queue)
{
    if (hasSubjectFilter()) {
        queue->setSubjectFilter(getSubjectFilter());
        active.push_back(&subjectFilter);
    }
    if (hasSelectorFilter()) {
        queue->setSelectorFilter(getSelectorFilter());
        active.push_back(&selectorFilter);
    }
}

void Filter::configure(QueueSettings& settings)
{
    if (hasSelectorFilter()) {
        settings.filter = getSelectorFilter();
        active.push_back(&selectorFilter);
    }
    if (nolocal) {
        settings.noLocal = true;
        QPID_LOG(notice, "No local filter set");
    }
}

std::string Filter::getBindingKey(boost::shared_ptr<Exchange> exchange) const
{
    if (subjectFilter.value.empty() && exchange->getType() == TopicExchange::typeName) {
        return WILDCARD;
    } else {
        return subjectFilter.value;
    }
}

void Filter::bind(boost::shared_ptr<Exchange> exchange, boost::shared_ptr<Queue> queue)
{
    qpid::framing::FieldTable bindingArgs;
    if (exchange->getType() == TopicExchange::typeName) {
        setDefaultSubjectFilter(true);
        active.push_back(&subjectFilter);
    } else if (exchange->getType() == DirectExchange::typeName) {
        if (!setDefaultSubjectFilter() && adjustDirectFilter()) {
            QPID_LOG(info, "Using legacy topic filter as a direct matching filter for " << exchange->getName());
        }
        active.push_back(&subjectFilter);
    } else if (exchange->getType() == HeadersExchange::typeName) {
        setDefaultHeadersFilter();
        qpid::amqp_0_10::translate(headersFilter.value, bindingArgs);
        active.push_back(&headersFilter);
    } else if (exchange->getType() == XML) {
        setDefaultXQueryFilter();
        if (!setDefaultSubjectFilter() && adjustDirectFilter()) {
            QPID_LOG(info, "Using legacy topic filter as a direct matching filter for " << exchange->getName());
        }
        bindingArgs.setString(XQUERY, xqueryFilter.value);
        active.push_back(&subjectFilter);
        active.push_back(&xqueryFilter);
    }
    queue->bind(exchange, subjectFilter.value, bindingArgs);
}

void Filter::setDefaultXQueryFilter()
{
    if (!xqueryFilter.requested) {
        xqueryFilter.key = DEFAULT_XQUERY_FILTER;
        xqueryFilter.value = DEFAULT_XQUERY_VALUE;
        xqueryFilter.setDescriptor(qpid::amqp::Descriptor(qpid::amqp::filters::XQUERY_FILTER_CODE));
    }
}
void Filter::setDefaultHeadersFilter()
{
    if (!headersFilter.requested) {
        headersFilter.key = DEFAULT_HEADERS_FILTER;
        headersFilter.value[XMATCH] = ALL;
        headersFilter.setDescriptor(qpid::amqp::Descriptor(qpid::amqp::filters::LEGACY_HEADERS_FILTER_CODE));
    }
}

bool Filter::setDefaultSubjectFilter(const qpid::amqp::Descriptor& d, const std::string& value)
{
    if (!subjectFilter.requested) {
        subjectFilter.key = DEFAULT_SUBJECT_FILTER;
        subjectFilter.value = value;
        subjectFilter.setDescriptor(d);
        return true;
    } else {
        return false;
    }
}

bool Filter::setDefaultSubjectFilter(bool wildcards)
{
    if (wildcards) {
        return setDefaultSubjectFilter(qpid::amqp::Descriptor(qpid::amqp::filters::LEGACY_TOPIC_FILTER_CODE), WILDCARD);
    } else {
        return setDefaultSubjectFilter(qpid::amqp::Descriptor(qpid::amqp::filters::LEGACY_DIRECT_FILTER_CODE));
    }
}

Filter::FilterBase::FilterBase() : described(false), requested(false), descriptor(0) {}
Filter::FilterBase::~FilterBase() {}
void Filter::FilterBase::setDescriptor(const qpid::amqp::Descriptor& d)
{
    described = true;
    descriptor = d;
}
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
qpid::amqp::Descriptor symbolicDescriptor(const std::string& symbol)
{
    qpid::amqp::CharSequence cs;
    cs.data = symbol.data();
    cs.size = symbol.size();
    return qpid::amqp::Descriptor(cs);
}
}

bool Filter::adjustDirectFilter()
{
    if (subjectFilter.descriptor.match(qpid::amqp::filters::LEGACY_TOPIC_FILTER_SYMBOL, qpid::amqp::filters::LEGACY_TOPIC_FILTER_CODE)) {
        if (subjectFilter.descriptor.type == qpid::amqp::Descriptor::NUMERIC) {
            subjectFilter.descriptor = qpid::amqp::Descriptor(qpid::amqp::filters::LEGACY_DIRECT_FILTER_CODE);
        } else {
            subjectFilter.descriptor = symbolicDescriptor(qpid::amqp::filters::LEGACY_DIRECT_FILTER_SYMBOL);
        }
        return true;
    } else {
        return false;
    }
}

void Filter::FilterBase::write(pn_data_t* data)
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
        writeValue(data);
        pn_data_exit(data);
    } else {
        writeValue(data);
    }
}
void Filter::StringFilter::writeValue(pn_data_t* data)
{
    pn_data_put_string(data, convert(value));
}

void Filter::MapFilter::writeValue(pn_data_t* data)
{
    pn_data_put_map(data);
    pn_data_enter(data);
    for (ValueType::const_iterator i = value.begin(); i != value.end(); ++i) {
        pn_data_put_string(data, convert(i->first));
        pn_data_put_string(data, convert(i->second));//TODO: other types?
    }
    pn_data_exit(data);
}

void Filter::write(std::map<std::string, qpid::types::Variant> source, pn_data_t* target)
{
    MapFilter dummy;
    dummy.value = source;
    dummy.writeValue(target);
}


}}} // namespace qpid::broker::amqp
