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

 /**
 * \file AioCallback.h
 */

#ifndef qpid_asyncStore_jrnl2_AioCallback_h_
#define qpid_asyncStore_jrnl2_AioCallback_h_

#include <stdint.h> // uint16_t
#include <vector>

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

class DataToken;

/**
 * \brief This pure virtual class provides an interface through which asyncronous operation completion calls may
 * be made.
 */
class AioCallback
{
public:
    /**
     * \brief Virtual destructor
     */
    virtual ~AioCallback() {}

    /**
     * \brief Callback function through which asynchronous IO write operation completion notifications are sent.
     */
    virtual void writeAioCompleteCallback(std::vector<DataToken*>& dataTokenList) = 0;

    /**
     * \brief Callback function through which asynchronous IO read operation completion notifications are sent.
     */
    virtual void readAioCompleteCallback(std::vector<uint16_t>& buffPageCtrlBlkIndexList) = 0;

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_AioCallback_h_

