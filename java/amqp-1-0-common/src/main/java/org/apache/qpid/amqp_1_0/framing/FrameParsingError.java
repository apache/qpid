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
package org.apache.qpid.amqp_1_0.framing;

public enum FrameParsingError
{
    UNDERSIZED_FRAME_HEADER,
    OVERSIZED_FRAME_HEADER,
    DATA_OFFSET_IN_HEADER,
    DATA_OFFSET_TOO_LARGE,
    UNKNOWN_FRAME_TYPE,
    CHANNEL_ID_BEYOND_MAX,
    SPARE_OCTETS_IN_FRAME_BODY,
    INSUFFICIENT_OCTETS_IN_FRAME_BODY,
    UNKNOWN_TYPE_CODE, UNPARSABLE_TYPE;
}
