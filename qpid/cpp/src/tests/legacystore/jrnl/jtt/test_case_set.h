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

#ifndef mrg_jtt_test_case_set_hpp
#define mrg_jtt_test_case_set_hpp

#include "test_case.h"

#include <cstddef>
#include <boost/tokenizer.hpp>
#include <map>
#include <vector>

namespace mrg
{
namespace jtt
{

    class test_case_set
    {
    public:
        enum csv_col_enum {
                CSV_TC_NUM = 0,
                CSV_TC_NUM_MSGS,
                CSV_TC_MIN_DATA_SIZE,
                CSV_TC_MAX_DATA_SIZE,
                CSV_TC_AUTO_DEQ,
                CSV_TC_MIN_XID_SIZE,
                CSV_TC_MAX_XID_SIZE,
                CSV_TC_TRANSIENT,
                CSV_TC_EXTERNAL,
                CSV_TC_COMMENT };
        typedef std::pair<unsigned, csv_col_enum> csv_pair;
        typedef std::map<unsigned, csv_col_enum> csv_map;
        typedef csv_map::const_iterator csv_map_citr;
        static csv_map std_csv_map;

        typedef std::vector<test_case::shared_ptr> tcl;
        typedef tcl::iterator tcl_itr;
        typedef tcl::const_iterator tcl_citr;

        typedef boost::tokenizer<boost::escaped_list_separator<char> > csv_tok;
        typedef csv_tok::const_iterator csv_tok_citr;

    private:
        tcl _tc_list;
        static const bool _map_init;
        unsigned _csv_ignored;

    public:
        test_case_set();
        test_case_set(const std::string& csv_filename, const bool recover_mode,
                const csv_map& cols = std_csv_map);
        virtual ~test_case_set();

         inline unsigned size() const { return _tc_list.size(); }
         inline unsigned ignored() const { return _csv_ignored; }
         inline bool empty() const { return _tc_list.empty(); }

        inline void append(const test_case::shared_ptr& tc) { _tc_list.push_back(tc); }
        void append(const unsigned test_case_num, const u_int32_t num_msgs,
                const std::size_t min_data_size, const std::size_t max_data_size,
                const bool auto_deq, const std::size_t min_xid_size,
                const std::size_t max_xid_size, const test_case::transient_t transient,
                const test_case::external_t external, const std::string& comment);
        void append_from_csv(const std::string& csv_filename, const bool recover_mode,
                const csv_map& cols = std_csv_map);
        inline tcl_itr begin() { return _tc_list.begin(); }
        inline tcl_itr end() { return _tc_list.end(); }
        inline const test_case::shared_ptr& operator[](unsigned i) { return _tc_list[i]; }
        inline void clear() { _tc_list.clear(); }

    private:
        test_case::shared_ptr get_tc_from_csv(const std::string& csv_line, const csv_map& cols);
        static bool __init();
    };

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_test_case_set_hpp
