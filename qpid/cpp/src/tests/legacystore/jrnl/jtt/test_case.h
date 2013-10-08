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

#ifndef mrg_jtt_test_case_hpp
#define mrg_jtt_test_case_hpp

#include <boost/shared_ptr.hpp>
#include <cstddef>
#include <map>
#include "test_case_result.h"
#include "test_case_result_agregation.h"
#include <vector>

namespace mrg
{
namespace jtt
{

    class test_case
    {
    public:
        enum transient_type { JTT_TRANSIENT = 0, JTT_PERSISTNET, JTT_RANDOM };
        typedef transient_type transient_t;

        enum data_location { JDL_INTERNAL = 0, JDL_EXTERNAL, JDL_RANDOM };
        typedef data_location external_t;

        typedef boost::shared_ptr<test_case> shared_ptr;

        typedef std::map<std::string, test_case_result_agregation::shared_ptr> res_map;
        typedef std::pair<std::string, test_case_result_agregation::shared_ptr> res_map_pair;
        typedef res_map::const_iterator res_map_citr;

    private:
        unsigned _test_case_num;
        u_int32_t _num_msgs;
        std::size_t _min_data_size;
        std::size_t _max_data_size;
        bool _auto_dequeue;
        // TODO: add probability of transaction to these params
        std::size_t _min_xid_size;
        std::size_t _max_xid_size;
        // TODO: change these enums (transient_t & external_t) to probabilities
        transient_t _transient;
        external_t _external;
        std::string _comment;

        test_case_result_agregation _result_average; // overall average result
        res_map _result_jmap; // map of per-journal averages

    public:
        test_case(const unsigned test_case_num, const u_int32_t num_msgs,
                const std::size_t min_data_size, const std::size_t max_data_size,
                const bool auto_deq,  const std::size_t min_xid_size,
                const std::size_t max_xid_size, const transient_t transient,
                const external_t external, const std::string& comment);
        virtual ~test_case();

        inline unsigned test_case_num() const { return _test_case_num; }
        inline u_int32_t num_msgs() const { return _num_msgs; }
        inline std::size_t min_data_size() const { return _min_data_size; }
        inline std::size_t max_data_size() const { return _max_data_size; }
        std::size_t this_data_size() const;
        inline bool auto_deq() const { return _auto_dequeue; }
        inline std::size_t min_xid_size() const { return _min_xid_size; }
        inline std::size_t max_xid_size() const { return _max_xid_size; }
        std::size_t this_xid_size() const;
        inline transient_t transient() const { return _transient; }
        bool this_transience() const;
        inline external_t external() const { return _external; }
        bool this_external() const;
        inline const std::string& comment() const { return _comment; }

        void add_result(test_case_result::shared_ptr& p);
        void set_fmt_chk_res(const bool res, const std::string& jid);

        inline const test_case_result_agregation& average() const { return _result_average; }
        inline u_int32_t num_results() const { return _result_average.num_results(); }
        inline unsigned num_jrnls() const { return _result_jmap.size(); }
        inline res_map_citr jrnl_average(std::string& jid) const { return _result_jmap.find(jid); }
        inline res_map_citr jmap_begin() const { return _result_jmap.begin(); }
        inline res_map_citr jmap_end() const { return _result_jmap.end(); }
        const test_case_result::shared_ptr jmap_last(std::string& jid) const;

        void clear();
        const std::string str() const;
    };

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_test_case_hpp
