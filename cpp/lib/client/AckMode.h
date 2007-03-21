#ifndef _client_AckMode_h
#define _client_AckMode_h

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

namespace qpid {
namespace client {

/**
 * The available acknowledgements modes.
 * 
 * \ingroup clientapi
 */
enum AckMode {
    /** No acknowledgement will be sent, broker can
        discard messages as soon as they are delivered
        to a consumer using this mode. **/
    NO_ACK     = 0,  
    /** Each message will be automatically
        acknowledged as soon as it is delivered to the
        application **/  
    AUTO_ACK   = 1,  
    /** Acknowledgements will be sent automatically,
        but not for each message. **/
    LAZY_ACK   = 2,
    /** The application is responsible for explicitly
        acknowledging messages. **/  
    CLIENT_ACK = 3 
};

}} // namespace qpid::client



#endif  /*!_client_AckMode_h*/
#ifndef _client_AckMode_h
#define _client_AckMode_h

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

namespace qpid {
namespace client {

/**
 * The available acknowledgements modes.
 * 
 * \ingroup clientapi
 */
enum AckMode {
    /** No acknowledgement will be sent, broker can
        discard messages as soon as they are delivered
        to a consumer using this mode. **/
    NO_ACK     = 0,  
    /** Each message will be automatically
        acknowledged as soon as it is delivered to the
        application **/  
    AUTO_ACK   = 1,  
    /** Acknowledgements will be sent automatically,
        but not for each message. **/
    LAZY_ACK   = 2,
    /** The application is responsible for explicitly
        acknowledging messages. **/  
    CLIENT_ACK = 3 
};

}} // namespace qpid::client



#endif  /*!_client_AckMode_h*/
