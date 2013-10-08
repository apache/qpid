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

#ifndef mrg_jtt_jrnl_init_params_hpp
#define mrg_jtt_jrnl_init_params_hpp

#include <boost/shared_ptr.hpp>
#include <string>
#include <sys/types.h>

namespace mrg
{
namespace jtt
{

    class jrnl_init_params
    {
    public:
        static const u_int16_t def_num_jfiles;
        static const bool def_ae;
        static const u_int16_t def_ae_max_jfiles;
        static const u_int32_t def_jfsize_sblks;
        static const u_int16_t def_wcache_num_pages;
        static const u_int32_t def_wcache_pgsize_sblks;

        typedef boost::shared_ptr<jrnl_init_params> shared_ptr;

    private:
        std::string _jid;
        std::string _jdir;
        std::string _base_filename;
        u_int16_t _num_jfiles;
        bool _ae;
        u_int16_t _ae_max_jfiles;
        u_int32_t _jfsize_sblks;
        u_int16_t _wcache_num_pages;
        u_int32_t _wcache_pgsize_sblks;

    public:
        jrnl_init_params(const std::string& jid, const std::string& jdir, const std::string& base_filename,
                const u_int16_t num_jfiles = def_num_jfiles, const bool ae = def_ae,
                const u_int16_t ae_max_jfiles = def_ae_max_jfiles, const u_int32_t jfsize_sblks = def_jfsize_sblks,
                const u_int16_t wcache_num_pages = def_wcache_num_pages,
                const u_int32_t wcache_pgsize_sblks = def_wcache_pgsize_sblks);
        jrnl_init_params(const jrnl_init_params& jp);
        jrnl_init_params(const jrnl_init_params* const jp_ptr);

        inline const std::string& jid() const { return _jid; }
        inline const std::string& jdir() const { return _jdir; }
        inline const std::string& base_filename() const { return _base_filename; }
        inline u_int16_t num_jfiles() const { return _num_jfiles; }
        inline bool is_ae() const { return _ae; }
        inline u_int16_t ae_max_jfiles() const { return _ae_max_jfiles; }
        inline u_int32_t jfsize_sblks() const { return _jfsize_sblks; }
        inline u_int16_t wcache_num_pages() const { return _wcache_num_pages; }
        inline u_int32_t wcache_pgsize_sblks() const { return _wcache_pgsize_sblks; }
    };

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_jrnl_init_params_hpp
