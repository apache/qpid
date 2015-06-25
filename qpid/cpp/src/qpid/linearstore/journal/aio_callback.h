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

#ifndef QPID_LINEARSTORE_JOURNAL_AIO_CALLBACK_H
#define QPID_LINEARSTORE_JOURNAL_AIO_CALLBACK_H

#include <stdint.h>
#include <vector>

namespace qpid {
namespace linearstore {
namespace journal {

class data_tok;

class aio_callback
{
public:
    virtual ~aio_callback() {}
    virtual void wr_aio_cb(std::vector<data_tok*>& dtokl) = 0;
    virtual void rd_aio_cb(std::vector<uint16_t>& pil) = 0;
};

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_AIO_CALLBACK_H
