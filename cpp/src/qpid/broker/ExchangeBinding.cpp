/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <qpid/broker/ExchangeBinding.h>
#include <qpid/broker/Exchange.h>

using namespace qpid::broker;
using namespace qpid::framing;

ExchangeBinding::ExchangeBinding(Exchange* _e, Queue::shared_ptr _q, const string& _key, FieldTable* _args) : e(_e), q(_q), key(_key), args(_args){}

void ExchangeBinding::cancel(){
    e->unbind(q, key, args);
    delete this;
}

ExchangeBinding::~ExchangeBinding(){
}
