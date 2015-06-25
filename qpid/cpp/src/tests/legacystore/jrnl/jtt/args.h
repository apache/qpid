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

#ifndef mrg_jtt_args_hpp
#define mrg_jtt_args_hpp

#include <boost/program_options.hpp>
#include "read_arg.h"

namespace mrg
{
namespace jtt
{

    struct args
    {
        boost::program_options::options_description _options_descr;
        boost::program_options::variables_map _vmap;

        // Add args here
        std::string jfile_analyzer;
        std::string test_case_csv_file_name;
        std::string journal_dir;
        bool format_chk;
        bool keep_jrnls;
        unsigned lld_rd_num;
        unsigned lld_skip_num;
        unsigned num_jrnls;
        unsigned pause_secs;
        bool randomize;
        read_arg read_mode;
        unsigned read_prob;
    	bool recover_mode;
        bool repeat_flag;
        bool reuse_instance;
        unsigned seed;

        args(std::string opt_title);
        bool parse(int argc, char** argv); // return true if error, false if ok
        bool usage() const; // return true
        void print_args() const;
        void print_flags() const;
    };

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_args_hpp
