#ifndef _QMF_SUBSCRIPTION_IMPL_H_
#define _QMF_SUBSCRIPTION_IMPL_H_
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

#include "qpid/RefCounted.h"
#include "qmf/Subscription.h"

namespace qmf {
    class SubscriptionImpl : public virtual qpid::RefCounted {
    public:
        //
        // Public impl-only methods
        //
        SubscriptionImpl(int p) : placeholder(p) {}
        ~SubscriptionImpl();

        //
        // Methods from API handle
        //
        void cancel();
        bool isActive() const;
        void lock();
        void unlock();
        uint32_t getDataCount() const;
        Data getData(uint32_t) const;

    private:
        int placeholder;
    };

    struct SubscriptionImplAccess
    {
        static SubscriptionImpl& get(Subscription&);
        static const SubscriptionImpl& get(const Subscription&);
    };
}

#endif
