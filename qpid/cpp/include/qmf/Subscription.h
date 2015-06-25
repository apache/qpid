#ifndef QMF_SUBSCRIPTION_H
#define QMF_SUBSCRIPTION_H
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

#if !defined(QMF_USE_DEPRECATED_API) && !defined(qmf2_EXPORTS) && !defined(SWIG)
#  error "The API defined in this file has been DEPRECATED and will be removed in the future."
#  error "Define 'QMF_USE_DEPRECATED_API' to enable continued use of the API."
#endif

#include <qmf/ImportExport.h>
#include "qmf/Handle.h"
#include "qpid/types/Variant.h"
#include <string>

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class SubscriptionImpl;
    class Data;

    class QMF_CLASS_EXTERN Subscription : public qmf::Handle<SubscriptionImpl> {
    public:
        QMF_EXTERN Subscription(SubscriptionImpl* impl = 0);
        QMF_EXTERN Subscription(const Subscription&);
        QMF_EXTERN Subscription& operator=(const Subscription&);
        QMF_EXTERN ~Subscription();

        /**
         * Construction:  A subscription is created by calling ConsoleSession::subscribe.
         */

        /**
         * Cancel subscriptions to all subscribed agents.  After this is called, the subscription
         * shall be inactive.
         */
        QMF_EXTERN void cancel();

        /**
         * Check to see if this subscription is active.  It is active if it has a live subscription
         * on at least one agent.  If it is not active, there is nothing that can be done to make it
         * active, it can only be deleted.
         */
        QMF_EXTERN bool isActive() const;

        /**
         * lock and unlock should be used to bracket a traversal of the data set.  After lock is called,
         * the subscription will not change its set of available data objects.  Between calls to getDataCount
         * and getData, no data objects will be added or removed.  After unlock is called, the set of data
         * will catch up to any activity that occurred while the lock was in effect.
         */
        QMF_EXTERN void lock();
        QMF_EXTERN void unlock();
        QMF_EXTERN uint32_t getDataCount() const;
        QMF_EXTERN Data getData(uint32_t) const;

#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<Subscription>;
        friend struct SubscriptionImplAccess;
#endif
    };

}

#endif
