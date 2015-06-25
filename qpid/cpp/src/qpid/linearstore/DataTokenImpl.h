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

#ifndef QPID_LINEARSTORE_DATATOKENIMPL_H
#define QPID_LINEARSTORE_DATATOKENIMPL_H

#include "qpid/linearstore/journal/data_tok.h"
#include "qpid/broker/PersistableMessage.h"
#include <boost/intrusive_ptr.hpp>

namespace qpid{
namespace linearstore{

class DataTokenImpl : public qpid::linearstore::journal::data_tok, public qpid::RefCounted
{
  private:
    boost::intrusive_ptr<qpid::broker::PersistableMessage> sourceMsg;
  public:
    DataTokenImpl();
    virtual ~DataTokenImpl();

    inline boost::intrusive_ptr<qpid::broker::PersistableMessage>& getSourceMessage() { return sourceMsg; }
    inline void setSourceMessage(const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg) { sourceMsg = msg; }
};

} // namespace msgstore
} // namespace mrg

#endif // ifndef QPID_LINEARSTORE_DATATOKENIMPL_H
