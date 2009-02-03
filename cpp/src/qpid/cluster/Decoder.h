#ifndef QPID_CLUSTER_DECODER_H
#define QPID_CLUSTER_DECODER_H

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

#include "ConnectionDecoder.h"
#include "types.h"
#include <boost/ptr_container/ptr_map.hpp>

namespace qpid {
namespace cluster {

class EventHeader;

/**
 * Holds a map of ConnectionDecoders. Decodes Events into EventFrames
 * and forwards EventFrames to a handler.
 *
 * THREAD UNSAFE: Called sequentially with un-decoded cluster events from CPG.
 */
class Decoder
{
  public:
    typedef boost::function<void(const EventFrame&)> Handler;

    Decoder(const Handler& h);

    /** Takes EventHeader + data rather than Event so that the caller can
     * pass a pointer to connection data or a CPG buffer directly without copy.
     */
    void decode(const EventHeader& eh, const void* data);

    /** Erase the decoder for a connection. */
    void erase(const ConnectionId&);

  private:
    typedef boost::ptr_map<ConnectionId, ConnectionDecoder> Map;
    Handler handler;
    Map map;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_DECODER_H*/
