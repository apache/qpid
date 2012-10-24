#ifndef QPID_SYS_SSL_SSLHANDLER_H
#define QPID_SYS_SSL_SSLHANDLER_H

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

#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/sys/SecuritySettings.h"

#include <boost/intrusive_ptr.hpp>

namespace qpid {

namespace framing {
    class ProtocolInitiation;
}

namespace sys {

class AsynchIO;
struct AsynchIOBufferBase;
class Socket;
class Timer;
class TimerTask;

namespace ssl {

class SslIO;

class SslHandler : public OutputControl {
    std::string identifier;
    SslIO* aio;
    ConnectionCodec::Factory* factory;
    ConnectionCodec* codec;
    bool readError;
    bool isClient;
    bool nodict;
    boost::intrusive_ptr<sys::TimerTask> timeoutTimerTask;

    void write(const framing::ProtocolInitiation&);
    qpid::sys::SecuritySettings getSecuritySettings(SslIO* aio);

  public:
    SslHandler(std::string id, ConnectionCodec::Factory* f, bool nodict);
    ~SslHandler();
    void init(SslIO* a, Timer& timer, uint32_t maxTime);

    void setClient() { isClient = true; }

    // Output side
    void abort();
    void activateOutput();
    void giveReadCredit(int32_t);

    // Input side
    void readbuff(qpid::sys::AsynchIO&, qpid::sys::AsynchIOBufferBase* buff);
    void eof(qpid::sys::AsynchIO&);
    void disconnect(qpid::sys::AsynchIO& a);

    // Notifications
    void nobuffs(qpid::sys::AsynchIO&);
    void idle(qpid::sys::AsynchIO&);
    void closedSocket(qpid::sys::AsynchIO&, const qpid::sys::Socket& s);
};

}}} // namespace qpid::sys::ssl

#endif  /*!QPID_SYS_SSL_SSLHANDLER_H*/
