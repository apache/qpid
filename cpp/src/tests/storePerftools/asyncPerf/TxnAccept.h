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
 * \file TxnAccept.h
 */

#ifndef tests_storePerftools_asyncPerf_TxnAccept_h_
#define tests_storePerftools_asyncPerf_TxnAccept_h_

#include "qpid/broker/TxnOp.h"

#include "boost/shared_ptr.hpp"
#include <deque>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class DeliveryRecord;

class TxnAccept: public qpid::broker::TxnOp {
public:
    TxnAccept(std::deque<boost::shared_ptr<DeliveryRecord> >& ops);
    virtual ~TxnAccept();

    // --- Interface TxnOp ---
    bool prepare(qpid::broker::TxnHandle& th) throw();
    void commit()  throw();
    void rollback()  throw();
private:
    std::deque<boost::shared_ptr<DeliveryRecord> > m_ops;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_TxnAccept_h_
