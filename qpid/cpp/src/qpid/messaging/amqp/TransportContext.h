#ifndef QPID_MESSAGING_AMQP_TRANSPORTCONTEXT_H
#define QPID_MESSAGING_AMQP_TRANSPORTCONTEXT_H

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
namespace qpid {
namespace sys {
class Codec;
}
namespace messaging {
struct ConnectionOptions;

namespace amqp {

/**
 * Interface to be supplied by 'users' of Transport interface, in
 * order to provide codec and handle callbaskc for opening and closing
 * of connection.
 */
class TransportContext
{
  public:
    virtual ~TransportContext() {}
    virtual qpid::sys::Codec& getCodec() = 0;
    virtual const qpid::messaging::ConnectionOptions* getOptions() = 0;
    virtual void closed() = 0;
    virtual void opened() = 0;
  private:
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_TRANSPORTCONTEXT_H*/
