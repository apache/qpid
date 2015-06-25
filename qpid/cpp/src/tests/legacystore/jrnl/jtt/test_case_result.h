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

#ifndef mrg_jtt_test_case_result_hpp
#define mrg_jtt_test_case_result_hpp

#include <boost/shared_ptr.hpp>
#include <deque>
#include "qpid/legacystore/jrnl/jexception.h"
#include "qpid/legacystore/jrnl/time_ns.h"
#include <string>

namespace mrg
{
namespace jtt
{

    class test_case_result
    {
    public:
        typedef boost::shared_ptr<test_case_result> shared_ptr;

        typedef std::deque<std::string> elist;
        typedef elist::const_iterator elist_citr;

    protected:
        std::string _jid;
        u_int32_t _num_enq;
        u_int32_t _num_deq;
        u_int32_t _num_read;  // Messages actually read
        u_int32_t _num_rproc; // Messages handled by read thread (not all are read)
        journal::time_ns _start_time;
        journal::time_ns _stop_time;
        bool _stopped;
        journal::time_ns _test_time;
        elist _exception_list;

    public:
        test_case_result(const std::string& jid);
        virtual ~test_case_result();

        inline const std::string& jid() const { return _jid; }
        inline u_int32_t num_enq() const { return _num_enq; }
        inline u_int32_t incr_num_enq() { return ++_num_enq; }
        inline u_int32_t num_deq() const { return _num_deq; }
        inline u_int32_t incr_num_deq() { return ++_num_deq; }
        inline u_int32_t num_read() const { return _num_read; }
        inline u_int32_t incr_num_read() { return ++_num_read; }
        inline u_int32_t num_rproc() const { return _num_rproc; }
        inline u_int32_t incr_num_rproc() { return ++_num_rproc; }

        inline const journal::time_ns& start_time() const { return _start_time; }
        inline void set_start_time() { ::clock_gettime(CLOCK_REALTIME, &_start_time); }
        inline const journal::time_ns& stop_time() const { return _stop_time; }
        inline void set_stop_time()
                { ::clock_gettime(CLOCK_REALTIME, &_stop_time); calc_test_time(); }
        inline void set_test_time(const journal::time_ns& ts) { _test_time = ts; }
        inline const journal::time_ns& test_time() const { return _test_time; }
        const std::string test_time_str() const;

        void add_exception(const journal::jexception& e, const bool set_stop_time_flag = true);
        void add_exception(const std::string& err_str, const bool set_stop_time_flag = true);
        void add_exception(const char* err_str, const bool set_stop_time_flag = true);
        inline bool exception() const { return _exception_list.size() > 0; }
        inline unsigned exception_count() const { return _exception_list.size(); }
        inline elist_citr begin() { return _exception_list.begin(); }
        inline elist_citr end() { return _exception_list.end(); }
        inline const std::string& operator[](unsigned i) { return _exception_list[i]; }

        void clear();
        const std::string str(const bool summary) const;

    protected:
        const std::string str_full() const;
        const std::string str_summary() const;
        void calc_test_time();
    };

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_test_case_result_hpp
