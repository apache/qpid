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

#include "qmf/PrivateImplRef.h"
#include "qmf/exceptions.h"
#include "qmf/SubscriptionImpl.h"
#include "qmf/DataImpl.h"

using namespace std;
using namespace qmf;
using qpid::types::Variant;

typedef PrivateImplRef<Subscription> PI;

Subscription::Subscription(SubscriptionImpl* impl) { PI::ctor(*this, impl); }
Subscription::Subscription(const Subscription& s) : qmf::Handle<SubscriptionImpl>() { PI::copy(*this, s); }
Subscription::~Subscription() { PI::dtor(*this); }
Subscription& Subscription::operator=(const Subscription& s) { return PI::assign(*this, s); }

void Subscription::cancel() { impl->cancel(); }
bool Subscription::isActive() const { return impl->isActive(); }
void Subscription::lock() { impl->lock(); }
void Subscription::unlock() { impl->unlock(); }
uint32_t Subscription::getDataCount() const { return impl->getDataCount(); }
Data Subscription::getData(uint32_t i) const { return impl->getData(i); }


void SubscriptionImpl::cancel()
{
}


bool SubscriptionImpl::isActive() const
{
    return false;
}


void SubscriptionImpl::lock()
{
}


void SubscriptionImpl::unlock()
{
}


uint32_t SubscriptionImpl::getDataCount() const
{
    return 0;
}


Data SubscriptionImpl::getData(uint32_t) const
{
    return Data();
}


SubscriptionImpl& SubscriptionImplAccess::get(Subscription& item)
{
    return *item.impl;
}


const SubscriptionImpl& SubscriptionImplAccess::get(const Subscription& item)
{
    return *item.impl;
}
