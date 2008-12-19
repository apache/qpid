#ifndef _sys_AsynchIOHandler_h
#define _sys_AsynchIOHandler_h
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

#include "OutputControl.h"
#include "ConnectionCodec.h"
#include "AtomicValue.h"
#include "Mutex.h"

namespace qpid {

namespace framing {
    class ProtocolInitiation;
}

namespace sys {

class AsynchIO;
struct AsynchIOBufferBase;
class Socket;

class AsynchIOHandler : public OutputControl {
    std::string identifier;
    AsynchIO* aio;
    ConnectionCodec::Factory* factory;
    ConnectionCodec* codec;
    bool readError;
    bool isClient;
    AtomicValue<int32_t> readCredit;
    static const int32_t InfiniteCredit = -1;
    Mutex creditLock;

    void write(const framing::ProtocolInitiation&);

  public:
    AsynchIOHandler(std::string id, ConnectionCodec::Factory* f);
    ~AsynchIOHandler();
    void init(AsynchIO* a, int numBuffs);

    void setClient() { isClient = true; }

    // Output side
    void close();
    void activateOutput();
    void giveReadCredit(int32_t credit);

    // Input side
    bool readbuff(AsynchIO& aio, AsynchIOBufferBase* buff);
    void eof(AsynchIO& aio);
    void disconnect(AsynchIO& aio);
	
    // Notifications
    void nobuffs(AsynchIO& aio);
    void idle(AsynchIO& aio);
    void closedSocket(AsynchIO& aio, const Socket& s);
};

}} // namespace qpid::sys

#endif // _sys_AsynchIOHandler_h
