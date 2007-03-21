#ifndef _framing_Responder_h
#define _framing_Responder_h

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

#include "AMQRequestBody.h"
#include "AMQResponseBody.h"

namespace qpid {
namespace framing {

/**
 * Manage response ids and response mark remotely initianted requests.
 *
 * THREAD UNSAFE: This class is called as frames are sent or received
 * sequentially on a connection, so it does not need to be thread safe.
 */
class Responder
{
  public:
    Responder();

    /** Called after receiving a request. */
    void received(const AMQRequestBody::Data& request);

    /** Called before sending a response to set respose data.  */
    void sending(AMQResponseBody::Data& response);

    /** Get the ID of the highest response acknowledged by the peer. */
    ResponseId getResponseMark() { return responseMark; }

    // TODO aconway 2007-01-14: Batching support - store unsent
    // Response for equality comparison with subsequent responses.
    // 

  private:
    ResponseId lastId;
    ResponseId responseMark;
};

}} // namespace qpid::framing



#endif  /*!_framing_Responder_h*/
