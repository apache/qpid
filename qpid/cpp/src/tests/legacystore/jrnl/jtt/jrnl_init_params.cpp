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

#include "jrnl_init_params.h"

namespace mrg
{
namespace jtt
{

jrnl_init_params::jrnl_init_params(const std::string& jid, const std::string& jdir, const std::string& base_filename,
        const u_int16_t num_jfiles, const bool ae, const u_int16_t ae_max_jfiles, const u_int32_t jfsize_sblks,
        const u_int16_t wcache_num_pages, const u_int32_t wcache_pgsize_sblks):
        _jid(jid),
        _jdir(jdir),
        _base_filename(base_filename),
        _num_jfiles(num_jfiles),
        _ae(ae),
        _ae_max_jfiles(ae_max_jfiles),
        _jfsize_sblks(jfsize_sblks),
        _wcache_num_pages(wcache_num_pages),
        _wcache_pgsize_sblks(wcache_pgsize_sblks)
{}

jrnl_init_params::jrnl_init_params(const jrnl_init_params& jp):
        _jid(jp._jid),
        _jdir(jp._jdir),
        _base_filename(jp._base_filename),
        _num_jfiles(jp._num_jfiles),
        _ae(jp._ae),
        _ae_max_jfiles(jp._ae_max_jfiles),
        _jfsize_sblks(jp._jfsize_sblks),
        _wcache_num_pages(jp._wcache_num_pages),
        _wcache_pgsize_sblks(jp._wcache_pgsize_sblks)
{}

jrnl_init_params::jrnl_init_params(const jrnl_init_params* const jp_ptr):
        _jid(jp_ptr->_jid),
        _jdir(jp_ptr->_jdir),
        _base_filename(jp_ptr->_base_filename),
        _num_jfiles(jp_ptr->_num_jfiles),
        _ae(jp_ptr->_ae),
        _ae_max_jfiles(jp_ptr->_ae_max_jfiles),
        _jfsize_sblks(jp_ptr->_jfsize_sblks),
        _wcache_num_pages(jp_ptr->_wcache_num_pages),
        _wcache_pgsize_sblks(jp_ptr->_wcache_pgsize_sblks)
{}

// static initializers

const u_int16_t jrnl_init_params::def_num_jfiles = 8;
const bool      jrnl_init_params::def_ae = false;
const u_int16_t jrnl_init_params::def_ae_max_jfiles = 0;
const u_int32_t jrnl_init_params::def_jfsize_sblks = 0xc00;
const u_int16_t jrnl_init_params::def_wcache_num_pages = 32;
const u_int32_t jrnl_init_params::def_wcache_pgsize_sblks = 64;

} // namespace jtt
} // namespace mrg
