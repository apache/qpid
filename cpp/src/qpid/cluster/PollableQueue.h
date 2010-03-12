#ifndef QPID_CLUSTER_POLLABLEQUEUE_H
#define QPID_CLUSTER_POLLABLEQUEUE_H

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

#include "qpid/sys/PollableQueue.h"
#include  <qpid/log/Statement.h>

namespace qpid {
namespace cluster {

/**
 * More convenient version of PollableQueue that handles iterating
 * over the batch and error handling.
 *
 * Constructed in "bypass" mode where items are processed directly
 * rather than put on the queue. This is important for the
 * PRE_INIT stage when Cluster is pumping CPG dispatch directly
 * before the poller has started.
 *
 * Calling start() starts the pollable queue and disabled bypass mode.
 */
template <class T> class PollableQueue : public sys::PollableQueue<T> {
  public:
    typedef boost::function<void (const T&)> Callback;
    typedef boost::function<void()> ErrorCallback;

    PollableQueue(Callback f, ErrorCallback err, const std::string& msg,
                  const boost::shared_ptr<sys::Poller>& poller)
        : sys::PollableQueue<T>(boost::bind(&PollableQueue<T>::handleBatch, this, _1),
                                poller),
          callback(f), error(err), message(msg), bypass(true)
    {}

    typename sys::PollableQueue<T>::Batch::const_iterator
    handleBatch(const typename sys::PollableQueue<T>::Batch& values) {
        try {
            typename sys::PollableQueue<T>::Batch::const_iterator i = values.begin();
            while (i != values.end() && !this->isStopped()) {
                callback(*i);
                ++i;
            }
            return i;
        }
        catch (const std::exception& e) {
            QPID_LOG(critical, message << ": " << e.what());
            this->stop();
            error();
            return values.end();
        }
    }

    void push(const T& t) {
        if (bypass) callback(t);
        else sys::PollableQueue<T>::push(t);
    }

    void start() {
        bypass = false;
        sys::PollableQueue<T>::start();
    }

  private:
    Callback callback;
    ErrorCallback error;
    std::string message;
    bool bypass;
};

    
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_POLLABLEQUEUE_H*/
