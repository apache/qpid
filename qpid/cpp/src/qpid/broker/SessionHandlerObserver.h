#ifndef QPID_BROKER_SESSIONHANDLEROBSERVER_H
#define QPID_BROKER_SESSIONHANDLEROBSERVER_H

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

#include "Observers.h"

namespace qpid {
namespace broker {
class SessionHandler;

/**
 * Observer of session handler events.
 */
class SessionHandlerObserver
{
  public:
    virtual ~SessionHandlerObserver() {}
    virtual void newSessionHandler(SessionHandler&) {}
};


class SessionHandlerObservers : public Observers<SessionHandlerObserver> {
  public:
    void newSessionHandler(SessionHandler& sh) {
        each(boost::bind(&SessionHandlerObserver::newSessionHandler, _1, boost::ref(sh)));
    }
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_SESSIONHANDLEROBSERVER_H*/
