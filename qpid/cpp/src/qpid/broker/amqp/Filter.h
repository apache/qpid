#ifndef QPID_BROKER_AMQP_FILTER_H
#define QPID_BROKER_AMQP_FILTER_H

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
#include "qpid/amqp/MapReader.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/types/Variant.h"
#include <map>
#include <vector>
#include <boost/shared_ptr.hpp>

struct pn_data_t;
namespace qpid {
namespace broker {
class Exchange;
class Queue;
struct QueueSettings;
namespace amqp {
class Outgoing;

class Filter : private qpid::amqp::MapReader
{
  public:
    Filter();
    void read(pn_data_t*);
    void write(pn_data_t*);
    std::string getBindingKey(boost::shared_ptr<Exchange> exchange) const;

    /**
     * Apply filters where source is a queue
     */
    void apply(boost::shared_ptr<Outgoing> queue);

    /**
     * Configure subscription queue for case where source is an exchange
     */
    void configure(QueueSettings&);
    /**
     * Bind subscription queue for case where source is an exchange
     */
    void bind(boost::shared_ptr<Exchange> exchange, boost::shared_ptr<Queue> queue);

    /**
     * Not really the ideal place for this, but the logic is already implemented here...
     */
    static void write(std::map<std::string, qpid::types::Variant> source, pn_data_t* target);
  private:
    struct FilterBase
    {
        bool described;
        bool requested;
        qpid::amqp::Descriptor descriptor;
        std::string key;
        FilterBase();
        virtual ~FilterBase();
        void write(pn_data_t*);
        virtual void writeValue(pn_data_t*) = 0;
        void setDescriptor(const qpid::amqp::Descriptor&);
    };
    struct StringFilter : FilterBase
    {
        std::string value;
        void writeValue(pn_data_t*);
    };
    struct MapFilter : FilterBase
    {
        typedef std::map<std::string, qpid::types::Variant> ValueType;
        ValueType value;
        void writeValue(pn_data_t*);
    };

    void onStringValue(const qpid::amqp::CharSequence& key, const qpid::amqp::CharSequence& value, const qpid::amqp::Descriptor* descriptor);
    void onNullValue(const qpid::amqp::CharSequence&, const qpid::amqp::Descriptor*);
    void onBooleanValue(const qpid::amqp::CharSequence&, bool, const qpid::amqp::Descriptor*);
    void onUByteValue(const qpid::amqp::CharSequence&, uint8_t, const qpid::amqp::Descriptor*);
    void onUShortValue(const qpid::amqp::CharSequence&, uint16_t, const qpid::amqp::Descriptor*);
    void onUIntValue(const qpid::amqp::CharSequence&, uint32_t, const qpid::amqp::Descriptor*);
    void onULongValue(const qpid::amqp::CharSequence&, uint64_t, const qpid::amqp::Descriptor*);
    void onByteValue(const qpid::amqp::CharSequence&, int8_t, const qpid::amqp::Descriptor*);
    void onShortValue(const qpid::amqp::CharSequence&, int16_t, const qpid::amqp::Descriptor*);
    void onIntValue(const qpid::amqp::CharSequence&, int32_t, const qpid::amqp::Descriptor*);
    void onLongValue(const qpid::amqp::CharSequence&, int64_t, const qpid::amqp::Descriptor*);
    void onFloatValue(const qpid::amqp::CharSequence&, float, const qpid::amqp::Descriptor*);
    void onDoubleValue(const qpid::amqp::CharSequence&, double, const qpid::amqp::Descriptor*);
    bool onStartListValue(const qpid::amqp::CharSequence& key, uint32_t count, const qpid::amqp::Descriptor* descriptor);
    bool onStartMapValue(const qpid::amqp::CharSequence& key, uint32_t count, const qpid::amqp::Descriptor* descriptor);
    void onEndMapValue(const qpid::amqp::CharSequence& key, uint32_t count, const qpid::amqp::Descriptor* descriptor);
    void setFilter(StringFilter&, const StringFilter&);
    bool hasSubjectFilter() const;
    std::string getSubjectFilter() const;
    bool hasSelectorFilter() const;
    std::string getSelectorFilter() const;
    bool setDefaultSubjectFilter(bool wildcards=false);
    bool setDefaultSubjectFilter(const qpid::amqp::Descriptor& descriptor, const std::string& value=std::string());
    bool adjustDirectFilter();
    void setDefaultHeadersFilter();
    void setDefaultXQueryFilter();

    StringFilter subjectFilter;
    StringFilter selectorFilter;
    StringFilter xqueryFilter;
    MapFilter headersFilter;
    std::vector<FilterBase*> active;
    bool inHeadersMap;
    bool nolocal;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_FILTER_H*/
