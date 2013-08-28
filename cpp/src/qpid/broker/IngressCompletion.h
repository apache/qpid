#ifndef QPID_BROKER_INGRESSCOMPLETION_H
#define QPID_BROKER_INGRESSCOMPLETION_H

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
#include "AsyncCompletion.h"
#include "qpid/sys/Mutex.h"
#include <boost/shared_ptr.hpp>
#include <boost/weak_ptr.hpp>
#include <vector>

namespace qpid {
namespace broker {

class Queue;
/**
 * An AsyncCompletion object for async enqueues, that can be flushed
 * when needed
 */
class IngressCompletion : public AsyncCompletion
{
  public:
    QPID_BROKER_EXTERN virtual ~IngressCompletion();

    void enqueueAsync(boost::shared_ptr<Queue>);
    void flush();
  private:
    typedef std::vector<boost::weak_ptr<Queue> > Queues;
    Queues queues;
    qpid::sys::Mutex lock;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_INGRESSCOMPLETION_H*/
