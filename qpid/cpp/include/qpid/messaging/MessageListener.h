#ifndef QPID_MESSAGING_MESSAGELISTENER_H
#define QPID_MESSAGING_MESSAGELISTENER_H

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
#include "qpid/client/ClientImportExport.h"

namespace qpid {
namespace messaging {

class Message;

/**
 * To use a push style interface for receiving messages, applications
 * provide implementations of this interface and pass an implementing
 * instance to MessageSource::subscribe().
 *
 * Messages arriving for that subscription will then be passed to the
 * implementation via the received() method.
 */
class MessageListener
{
  public:
    QPID_CLIENT_EXTERN virtual ~MessageListener() {}
    virtual void received(Message&) = 0;
  private:
};

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_MESSAGELISTENER_H*/
