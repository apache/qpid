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

#include "test_case_result.h"

#include <iomanip>
#include <sstream>

namespace mrg
{
namespace jtt
{

test_case_result::test_case_result(const std::string& jid):
        _jid(jid),
        _num_enq(0),
        _num_deq(0),
        _num_read(0),
        _num_rproc(0),
        _start_time(),
        _stop_time(),
        _stopped(false),
        _test_time(),
        _exception_list()
{}

test_case_result::~test_case_result()
{}

const std::string
test_case_result::test_time_str() const
{
    return _test_time.str(9);
}

void
test_case_result::add_exception(const journal::jexception& e, const bool set_stop_time_flag)
{
    if (!_stopped && set_stop_time_flag)
    {
        set_stop_time();
        _stopped = true;
    }
    _exception_list.push_back(e.what());
}

void
test_case_result::add_exception(const std::string& err_str, const bool set_stop_time_flag)
{
    if (!_stopped && set_stop_time_flag)
    {
        set_stop_time();
        _stopped = true;
    }
    _exception_list.push_back(err_str);
}

void
test_case_result::add_exception(const char* err_str, const bool set_stop_time_flag)
{
    if (!_stopped && set_stop_time_flag)
    {
        set_stop_time();
        _stopped = true;
    }
    _exception_list.push_back(err_str);
}

void
test_case_result::clear()
{
    _num_enq = 0;
    _num_deq = 0;
    _num_read = 0;
    _start_time.set_zero();
    _stop_time.set_zero();
    _test_time.set_zero();
    _exception_list.clear();
}

const std::string
test_case_result::str(const bool summary) const
{
    std::ostringstream oss;
    if (summary)
    {
        oss << _jid << ":";
        oss << str_summary();
        if (_exception_list.size())
            oss << "; fail: " << _exception_list[0] << std::endl;
        else
            oss << "; ok" << std::endl;
    }
    else
    {
        oss << "--- Journal instance: jid=\"" << _jid << "\" ---" << std::endl;
        oss << str_full();
        if (_exception_list.size())
            oss << "       exception/error:" << _exception_list[0] << std::endl;
    }
    return oss.str();
}

const std::string
test_case_result::str_full() const
{
    const double t = _test_time.tv_sec + (_test_time.tv_nsec/1e9);
    const bool no_exception = _exception_list.empty();
    std::ostringstream oss;
    oss.setf(std::ios::fixed, std::ios::floatfield);
    oss.precision(2);
    if (no_exception)
    {
        oss.precision(6);
        oss << "       total test time: " << t << "s" << std::endl;
    }
    oss.precision(3);
    oss << " total number enqueues: " << _num_enq;
    if (no_exception)
        oss << " (" << (_num_enq / t) << " enq/sec)";
    oss << std::endl;
    oss << " total number dequeues: " << _num_deq;
    if (no_exception)
        oss << " (" << (_num_deq / t) << " deq/sec)";
    oss << std::endl;
    oss << "total write operations: " << (_num_enq + _num_deq);
    if (no_exception)
        oss << " (" << ((_num_enq + _num_deq) / t) << " wrops/sec)";
    oss << std::endl;
    oss << "    total number reads: " << _num_read;
    if (no_exception)
        oss << " (" << (_num_read / t) << " rd/sec)";
    oss << std::endl;
    oss << "      total operations: " << (_num_enq + _num_deq + _num_read);
    if (no_exception)
        oss << " (" << ((_num_enq + _num_deq + _num_read) / t) << " ops/sec)";
    oss << std::endl;
    oss << "        overall result: " << (no_exception ? "PASS" : "*** FAIL ***") << std::endl;
    return oss.str();
}

const std::string
test_case_result::str_summary() const
{
    const double t = _test_time.tv_sec + (_test_time.tv_nsec/1e9);
    const bool no_exception = _exception_list.empty();
    std::ostringstream oss;
    oss.setf(std::ios::fixed, std::ios::floatfield);
    if (no_exception)
    {
        oss.precision(6);
        oss << " t=" << t << "s;";
    }
    else
        oss << " exception";
    oss.precision(3);
    oss << " enq=" << _num_enq;
    if (no_exception)
        oss << " (" << (_num_enq / t) << ")";
    oss << "; deq=" << _num_deq;
    if (no_exception)
        oss << " (" << (_num_deq / t) << ")";
    oss << "; wr=" << (_num_enq + _num_deq);
    if (no_exception)
        oss << " (" << ((_num_enq + _num_deq) / t) << ")";
    oss << "; rd=" << _num_read;
    if (no_exception)
        oss << " (" << (_num_read / t) << ")";
    oss << "; tot=" << (_num_enq + _num_deq + _num_read);
    if (no_exception)
        oss << " (" << ((_num_enq + _num_deq + _num_read) / t) << ")";
    return oss.str();
}

void
test_case_result::calc_test_time()
{
    if (!_start_time.is_zero() && _stop_time >= _start_time)
        _test_time = _stop_time - _start_time;
}

} // namespace jtt
} // namespace mrg
