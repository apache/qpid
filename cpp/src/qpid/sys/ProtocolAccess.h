#ifndef _sys_ProtocolAccess_h
#define _sys_ProtocolAccess_h

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

#include "AsynchIO.h"
#include "AsynchIOHandler.h"
#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>

namespace qpid {

namespace broker
{
class Connection;
}

namespace sys {

class ProtocolAccess
{
public:
    typedef boost::function0<void> Callback;
    typedef boost::function2<void, int, std::string> ClosedCallback;
    typedef boost::function1<void, boost::shared_ptr<broker::Connection> > SetConnCallback;

    ProtocolAccess (Callback ecb, ClosedCallback ccb, SetConnCallback sccb)
        : aio(0), establishedCb(ecb), closedCb(ccb), setConnCb(sccb) {}
    ~ProtocolAccess() {}
    inline void close() { if (aio) aio->queueWriteClose(); }

    inline void setAio(AsynchIO *_aio) { aio = _aio; establishedCb(); }
    inline void closedEof(AsynchIOHandler* async) { async->eof(*aio); closedCb(-1, "Closed by Peer"); }
    inline void closed(int err, std::string str) { closedCb(err, str); }
    inline void callConnCb(boost::shared_ptr<broker::Connection> c) { setConnCb(c); }

private:
    AsynchIO*       aio;
    Callback        establishedCb;
    ClosedCallback  closedCb;
    SetConnCallback setConnCb;
};

}}

#endif  //!_sys_ProtocolAccess_h
