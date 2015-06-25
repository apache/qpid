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

#ifndef mrg_jtt_test_mgr_hpp
#define mrg_jtt_test_mgr_hpp

#include "args.h"
#include <csignal>
#include <cstdlib>
#include "jrnl_instance.h"

namespace mrg
{
namespace jtt
{
    class test_mgr
    {
    public:
        typedef std::vector<jrnl_instance::shared_ptr> ji_list;
        typedef ji_list::iterator ji_list_itr;
        typedef ji_list::const_iterator ji_list_citr;

    private:
        ji_list _ji_list;
        args& _args;
        bool _err_flag;
        ptrdiff_t (*_random_fn_ptr)(const ptrdiff_t i);
        static volatile std::sig_atomic_t _signal;
        static volatile bool _abort;

    public:
        test_mgr(args& args);
        virtual ~test_mgr();
        void run();
        inline bool error() const { return _err_flag; }

        static void signal_handler(int signal);

    private:
        static bool exists(std::string file_name);
        void initialize_jrnls();
        void print_results(test_case::shared_ptr tcp, const bool summary);
        inline static ptrdiff_t random_fn(const ptrdiff_t i)
                { return static_cast<ptrdiff_t>(1.0 * i * std::rand() / RAND_MAX); }
    };

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_test_mgr_hpp
