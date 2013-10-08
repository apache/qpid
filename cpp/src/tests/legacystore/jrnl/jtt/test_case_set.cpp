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

#include "test_case_set.h"

#include <cstdlib>
#include <fstream>
#include <iostream>

namespace mrg
{
namespace jtt
{

test_case_set::test_case_set():
        _tc_list(),
        _csv_ignored(0)
{}

test_case_set::test_case_set(const std::string& csv_filename, const bool recover_mode,
        const csv_map& cols):
        _tc_list(),
        _csv_ignored(0)
{
    append_from_csv(csv_filename, recover_mode, cols);
}

test_case_set::~test_case_set()
{}

void
test_case_set::append(const unsigned test_case_num, const u_int32_t num_msgs,
        const std::size_t min_data_size, const std::size_t max_data_size, const bool auto_deq,
        const std::size_t min_xid_size, const std::size_t max_xid_size,
        const test_case::transient_t transient, const test_case::external_t external,
        const std::string& comment)
{
    test_case::shared_ptr tcp(new test_case(test_case_num, num_msgs, min_data_size,
            max_data_size, auto_deq, min_xid_size, max_xid_size, transient, external, comment));
    append(tcp);
}


#define CSV_BUFF_SIZE 2048
void
test_case_set::append_from_csv(const std::string& csv_filename, const bool recover_mode,
        const csv_map& cols)
{
    char buff[CSV_BUFF_SIZE];
    std::ifstream ifs(csv_filename.c_str());
    while (ifs.good())
    {
        ifs.getline(buff, (std::streamsize)CSV_BUFF_SIZE);
        if (ifs.gcount())
        {
            test_case::shared_ptr tcp = get_tc_from_csv(buff, cols);
            if (tcp.get())
            {
                if (!recover_mode || tcp->auto_deq())
                    append(tcp);
                else
                    _csv_ignored++;
            }
        }
    }
}

test_case::shared_ptr
test_case_set::get_tc_from_csv(const std::string& csv_line, const csv_map& cols)
{
    unsigned test_case_num = 0;
    u_int32_t num_msgs = 0;
    std::size_t min_data_size = 0;
    std::size_t max_data_size = 0;
    bool auto_deq = false;
    std::size_t min_xid_size = 0;
    std::size_t max_xid_size = 0;
    test_case::transient_t transient = test_case::JTT_TRANSIENT;
    test_case::external_t external = test_case::JDL_INTERNAL;
    std::string comment;

    csv_tok t(csv_line);
    unsigned col_num = 0;
    for (csv_tok_citr t_itr = t.begin(); t_itr != t.end(); ++t_itr, ++col_num)
    {
        const std::string& tok = *t_itr;
        csv_map_citr m_citr = cols.find(col_num);
        if (m_citr != cols.end())
        {
            switch (m_citr->second)
            {
                case CSV_TC_NUM:
                    if (!tok.size() || tok[0] < '0' || tok[0] > '9')
                        return test_case::shared_ptr();
                    test_case_num = unsigned(std::atol(tok.c_str()));
                    break;
                case CSV_TC_NUM_MSGS: num_msgs = u_int32_t(std::atol(tok.c_str())); break;
                case CSV_TC_MIN_DATA_SIZE: min_data_size = std::size_t(std::atol(tok.c_str())); break;
                case CSV_TC_MAX_DATA_SIZE: max_data_size = std::size_t(std::atol(tok.c_str())); break;
                case CSV_TC_AUTO_DEQ:
                    if (tok == "TRUE" || tok == "1")
                        auto_deq = true;
                    break;
                case CSV_TC_MIN_XID_SIZE: min_xid_size = std::size_t(std::atol(tok.c_str())); break;
                case CSV_TC_MAX_XID_SIZE: max_xid_size = std::size_t(std::atol(tok.c_str())); break;
                case CSV_TC_TRANSIENT:
                    if (tok == "TRUE" || tok == "1")
                        transient = test_case::JTT_PERSISTNET;
                    else if (tok == "RANDOM" || tok == "-1")
                        transient = test_case::JTT_RANDOM;
                    break;
                case CSV_TC_EXTERNAL:
                    if (tok == "TRUE" || tok == "1")
                        external = test_case::JDL_EXTERNAL;
                    else if (tok == "RANDOM" || tok == "-1")
                       external  = test_case::JDL_RANDOM;
                    break;
                case CSV_TC_COMMENT: comment = *t_itr; break;
            }
        }
    }
    if (col_num)
        return test_case::shared_ptr(new test_case(test_case_num, num_msgs, min_data_size,
                max_data_size, auto_deq, min_xid_size, max_xid_size, transient, external, comment));
    else
        return test_case::shared_ptr();
}

// Static member initializations
// This csv_map is for use on the standard spreadsheet-derived test case csv files.
test_case_set::csv_map test_case_set::std_csv_map;
const bool test_case_set::_map_init = __init();

bool
test_case_set::__init()
{
    std_csv_map.insert(test_case_set::csv_pair(0, test_case_set::CSV_TC_NUM));
    std_csv_map.insert(test_case_set::csv_pair(5, test_case_set::CSV_TC_NUM_MSGS));
    std_csv_map.insert(test_case_set::csv_pair(7, test_case_set::CSV_TC_MIN_DATA_SIZE));
    std_csv_map.insert(test_case_set::csv_pair(8, test_case_set::CSV_TC_MAX_DATA_SIZE));
    std_csv_map.insert(test_case_set::csv_pair(11, test_case_set::CSV_TC_AUTO_DEQ));
    std_csv_map.insert(test_case_set::csv_pair(9, test_case_set::CSV_TC_MIN_XID_SIZE));
    std_csv_map.insert(test_case_set::csv_pair(10, test_case_set::CSV_TC_MAX_XID_SIZE));
    std_csv_map.insert(test_case_set::csv_pair(12, test_case_set::CSV_TC_TRANSIENT));
    std_csv_map.insert(test_case_set::csv_pair(13, test_case_set::CSV_TC_EXTERNAL));
    std_csv_map.insert(test_case_set::csv_pair(20, test_case_set::CSV_TC_COMMENT));
    return true;
}

} // namespace jtt
} // namespace mrg
