/*
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
 */

#ifndef qpid_broker_AsyncStoreErrors_h_
#define qpid_broker_AsyncStoreErrors_h_

#include <stdint.h> // uint16_t, uint32_t

namespace qpid {
namespace broker {

    // Error codes returned by AsyncStore::submit()
    static const int ERR_OK = 0;        ///< Successful submission
    static const int ERR_PARAMS = -1;   ///< Incorrect or bad parameters in struct AsyncStoreOp
    static const int ERR_FULL = -2;     ///< Event queue full

    /**
     * This system allows 32-bit error codes to be expressed as either a struct of two
     * uint16_t value or a single uint32_t value.
     *
     * The idea of having two values is that the interface can possibly specify error classes
     * or types (possibly indicating severity too), while allowing the implementation to specify
     * an additional code or "sub-code" specific to that implementation.
     *
     * \todo: Finalize structure (another option: uint8_t class; uint8_t op; uint16_t errCode;)
     * and the interface-level error codes.
     */
#pragma pack(1)
    typedef struct {
        uint16_t errClass;
        uint16_t errorCode;
    } asyncStoreErrorStruct_t;
#pragma pack()

    typedef union {
        asyncStoreErrorStruct_t s;
        uint32_t i;
    } asyncStoreError_t;

}} // namespace qpid::broker


#endif // qpid_broker_AsyncStoreErrors_h_
