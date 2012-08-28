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
 * \file SimpleTxnAccept.h
 */

#ifndef tests_storePerftools_asyncPerf_SimpleTxnAccept_h_
#define tests_storePerftools_asyncPerf_SimpleTxnAccept_h_

#include "qpid/broker/SimpleTxnOp.h"

#include "boost/shared_ptr.hpp"
#include <deque>

namespace qpid {
namespace broker {

class SimpleDeliveryRecord;

class SimpleTxnAccept: public SimpleTxnOp {
public:
    SimpleTxnAccept(std::deque<boost::shared_ptr<SimpleDeliveryRecord> >& ops);
    virtual ~SimpleTxnAccept();

    // --- Interface TxnOp ---
    bool prepare(SimpleTxnBuffer* tb) throw();
    void commit() throw();
    void rollback() throw();
private:
    std::deque<boost::shared_ptr<SimpleDeliveryRecord> > m_ops;
};

}} // namespace qpid::broker

#endif // tests_storePerftools_asyncPerf_SimpleTxnAccept_h_
