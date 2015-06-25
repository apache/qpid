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

#ifndef mrg_jtt_data_src_hpp
#define mrg_jtt_data_src_hpp

#include <cstddef>
#include "qpid/legacystore/jrnl/slock.h"
#include "qpid/legacystore/jrnl/smutex.h"
#include <pthread.h>
#include <string>
#include <sys/types.h>

#define DATA_SIZE 1024 * 1024
#define XID_SIZE  1024 * 1024

namespace mrg
{
namespace jtt
{
    class data_src
    {
    public:
        static const std::size_t max_dsize = DATA_SIZE;
        static const std::size_t max_xsize = XID_SIZE;

    private:
        static char _data_src[];
        static char _xid_src[];
        static u_int64_t _xid_cnt;
        static bool _initialized;
        static mrg::journal::smutex _sm;

    public:
        static const char* get_data(const std::size_t offs);
        static std::string get_xid(const std::size_t xid_size);

    private:
        data_src();
        static u_int64_t get_xid_cnt() { mrg::journal::slock s(_sm); return _xid_cnt++; }
        static const char* get_xid_content(const std::size_t offs);
        static bool __init();
    };

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_data_src_hpp
