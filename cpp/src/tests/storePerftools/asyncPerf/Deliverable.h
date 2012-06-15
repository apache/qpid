/*
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
 */

/**
 * \file Deliverable.h
 */

#ifndef tests_storePerftools_asyncPerf_Deliverable_h_
#define tests_storePerftools_asyncPerf_Deliverable_h_

#include <boost/shared_ptr.hpp>
#include <stdint.h> // uint64_t

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class SimplePersistableMessage;
class SimplePersistableQueue;

class Deliverable
{
public:
    Deliverable();
    virtual ~Deliverable();

    virtual uint64_t contentSize() = 0;
    virtual void deliverTo(const boost::shared_ptr<SimplePersistableQueue>& queue) = 0;
    virtual SimplePersistableMessage& getMessage() = 0;
    virtual bool isDelivered() const;

protected:
    bool m_delivered;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_Deliverable_h_
