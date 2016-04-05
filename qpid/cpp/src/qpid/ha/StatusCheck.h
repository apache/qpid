#ifndef QPID_HA_STATUSCHECK_H
#define QPID_HA_STATUSCHECK_H

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

#include "BrokerInfo.h"
#include "Settings.h"
#include "qpid/Url.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Time.h"
#include <vector>

namespace qpid {
namespace ha {

class HaBroker;

// TODO aconway 2012-12-21: This solution is incomplete. It will only protect
// against bad promotion if there are READY brokers when this broker starts.
// It will not help the situation where brokers became READY after this one starts.
//

/**
 * Check whether a JOINING broker can be promoted .
 *
 * A JOINING broker can be promoted as long as all the other brokers are also
 * JOINING. If there are READY brokers in the cluster the JOINING broker should
 * refuse to promote so that one of the READY brokers can. This situation
 * only comes about if the primary is dead and no new primary has been promoted.
 *
 * THREAD SAFE: setUrl and canPromote are called in arbitrary management threads.
 */
class StatusCheck
{
  public:
    StatusCheck(HaBroker&);
    ~StatusCheck();
    void setUrl(const Url&);
    bool canPromote();

  private:
    void noPromote();
    void endThread();

    sys::Mutex lock;
    std::vector<sys::Thread> threads;
    sys::AtomicValue<int> threadCount;
    bool promote;
    const Settings settings;
    const sys::Duration heartbeat;
    const BrokerInfo brokerInfo;

  friend class StatusCheckThread;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_STATUSCHECK_H*/
