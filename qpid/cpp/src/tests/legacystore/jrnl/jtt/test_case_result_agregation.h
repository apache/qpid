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

#ifndef mrg_jtt_test_case_result_agregation_hpp
#define mrg_jtt_test_case_result_agregation_hpp

#include "test_case_result.h"

#include <iostream>
#include <vector>

namespace mrg
{
namespace jtt
{

    class test_case_result_agregation : public test_case_result
    {
    public:
        typedef boost::shared_ptr<test_case_result_agregation> shared_ptr;

        typedef std::vector<test_case_result::shared_ptr> tcrp_list;
        typedef tcrp_list::const_iterator tcrp_list_citr;

    private:
        bool _tc_average;
        bool _fmt_chk_done;
        bool _fmt_chk_err;
        tcrp_list _res_list;

    public:
        test_case_result_agregation(); // used for average across jrnl instances
        test_case_result_agregation(const std::string& jid);
        virtual ~test_case_result_agregation();

        void add_test_result(const test_case_result::shared_ptr& tcrp);

        inline bool tc_average_mode() const { return _tc_average; }
        inline bool fmt_chk_done() const { return _fmt_chk_done; }
        inline bool fmt_chk_res() const { return _fmt_chk_err; }
        inline void set_fmt_chk_res(const bool err)
                { _fmt_chk_done = true; _fmt_chk_err |= err; if (err) add_exception("Journal format error"); }
        inline u_int32_t num_results() const { return _res_list.size(); }
        inline tcrp_list_citr rlist_begin() const { return _res_list.begin(); }
        inline tcrp_list_citr rlist_end() const { return _res_list.end(); }
        inline const test_case_result::shared_ptr& operator[](unsigned i) const
                { return _res_list[i]; }
        bool exception() const;
        unsigned exception_count() const;

        void clear();
        const std::string str(const bool last_only, const bool summary) const;

    private:
        const std::string str_full(const bool last_only) const;
        const std::string str_summary(const bool last_only) const;
        const journal::time_ns& add_test_time(const journal::time_ns& t);
    };

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_test_case_result_agregation_hpp
