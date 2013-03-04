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
#include <boost/shared_ptr.hpp>

struct pn_data_t;
namespace qpid {
namespace broker {
class Exchange;
class Queue;
namespace amqp {


class Filter : qpid::amqp::MapReader
{
  public:
    void read(pn_data_t*);
    void write(pn_data_t*);
    bool hasSubjectFilter() const;
    std::string getSubjectFilter() const;
    bool hasSelectorFilter() const;
    std::string getSelectorFilter() const;
    void bind(boost::shared_ptr<Exchange> exchange, boost::shared_ptr<Queue> queue);
  private:
    struct StringFilter
    {
        bool described;
        qpid::amqp::Descriptor descriptor;
        std::string key;
        std::string value;
        StringFilter();
        void write(pn_data_t*);
        void bind(boost::shared_ptr<Exchange> exchange, boost::shared_ptr<Queue> queue);
    };

    void onStringValue(const qpid::amqp::CharSequence& key, const qpid::amqp::CharSequence& value, const qpid::amqp::Descriptor* descriptor);
    void setSubjectFilter(const StringFilter&);
    void setSelectorFilter(const StringFilter&);

    StringFilter subjectFilter;
    StringFilter selectorFilter;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_FILTER_H*/
