#ifndef _framing_Requester_h
#define _framing_Requester_h

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

#include <set>
#include "AMQRequestBody.h"
#include "AMQResponseBody.h"

namespace qpid {
namespace framing {

class AMQRequestBody;
class AMQResponseBody;

/**
 * Manage request IDs and the response mark for locally initiated requests.
 *
 * THREAD UNSAFE: This class is called as frames are sent or received
 * sequentially on a connection, so it does not need to be thread safe.
 */
class Requester
{
  public:
    Requester();

    /** Called before sending a request to set request data. */
    void sending(AMQRequestBody::Data&);

    /** Called after processing a response. */
    void processed(const AMQResponseBody::Data&);

	/** Get the next request id to be used. */
	RequestId getNextId() { return lastId + 1; }
	/** Get the first request acked by this response */
	RequestId getFirstAckRequest() { return firstAckRequest; }
	/** Get the last request acked by this response */
	RequestId getLastAckRequest() { return lastAckRequest; }

  private:
    RequestId lastId;
    ResponseId responseMark;
    ResponseId firstAckRequest;
    ResponseId lastAckRequest;
};

}} // namespace qpid::framing



#endif  /*!_framing_Requester_h*/
