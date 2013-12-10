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

#include "test_case_result_agregation.h"

#include <iomanip>
#include <sstream>

namespace mrg
{
namespace jtt
{

test_case_result_agregation::test_case_result_agregation():
        test_case_result("Average"),
        _tc_average(true),
        _fmt_chk_done(false),
        _fmt_chk_err(false),
        _res_list()
{
}

test_case_result_agregation::test_case_result_agregation(const std::string& jid):
        test_case_result(jid),
        _tc_average(false),
        _fmt_chk_done(false),
        _fmt_chk_err(false),
        _res_list()
{}

test_case_result_agregation::~test_case_result_agregation()
{}

void
test_case_result_agregation::add_test_result(const test_case_result::shared_ptr& tcrp)
{
    if (_tc_average || _jid.compare(tcrp->jid()) == 0)
    {
        _num_enq += tcrp->num_enq();
        _num_deq += tcrp->num_deq();
        _num_read += tcrp->num_read();
        add_test_time(tcrp->test_time());
        _exception_list.insert(_exception_list.end(), tcrp->begin(), tcrp->end());
        _res_list.push_back(tcrp);
    }
}

bool
test_case_result_agregation::exception() const
{
    for (tcrp_list_citr i = _res_list.begin(); i < _res_list.end(); i++)
        if ((*i)->exception())
            return true;
    return false;
}

unsigned
test_case_result_agregation::exception_count() const
{
    unsigned cnt = 0;
    for (tcrp_list_citr i = _res_list.begin(); i < _res_list.end(); i++)
        cnt += (*i)->exception_count();
    return cnt;
}

void
test_case_result_agregation::clear()
{
    test_case_result::clear();
    _res_list.clear();
}

const std::string
test_case_result_agregation::str(const bool last_only, const bool summary) const
{
    std::ostringstream oss;
    if (last_only)
        oss << "  " << _res_list.at(_res_list.size()-1)->str(summary);
    else
    {
        for (tcrp_list_citr i=_res_list.begin(); i!=_res_list.end(); i++)
            oss << "  " << (*i)->str(summary);
    }
    if (_res_list.size() > 1)
         oss << "  " << (summary ? str_summary(last_only) : str_full(last_only));
    return oss.str();
}

const std::string
test_case_result_agregation::str_full(const bool /*last_only*/) const
{
    std::ostringstream oss;
    oss.precision(2);
    if (_tc_average)
        oss << "Average across all journal instances:" << std::endl;
    else
        oss << "Average for jid=\"" << _jid << "\":" << std::endl;
    oss << "  total number results: " << _res_list.size() << std::endl;
    oss << "     number exceptions: " << _exception_list.size() << " (" <<
            (100.0 * _res_list.size() / _exception_list.size()) << "%)" << std::endl;

    oss << test_case_result::str_full();

    if (_exception_list.size())
    {
        unsigned n = 0;
        oss << "List of exceptions/errors:" << std::endl;
        for (elist_citr i = _exception_list.begin(); i != _exception_list.end(); i++, n++)
            oss << "  " << n << ". " << (*i) << std::endl;
    }

    if (!_tc_average && _res_list.size() > 1)
    {
        oss << "Individual results:" << std::endl;
        for (tcrp_list_citr i=_res_list.begin(); i!=_res_list.end(); i++)
            oss << "  " << (*i)->str(false) << std::endl;
        oss << std::endl;
    }

    return oss.str();
}

const std::string
test_case_result_agregation::str_summary(const bool /*last_only*/) const
{
    std::ostringstream oss;
    if (_tc_average)
        oss << "overall average [" << _res_list.size() << "]:";
    else
        oss << "average (" << _res_list.size() << "):";

    oss << test_case_result::str_summary();
    if (_fmt_chk_done)
        oss << " fmt-chk=" << (_fmt_chk_err ? "fail" : "ok");

    if (_exception_list.size())
    {
        if (_tc_average)
            oss << " fail: " << _exception_list.size() << " exception"
                << (_exception_list.size()>1?"s":"") << std::endl;
        else
        {
            if (_exception_list.size() == 1)
                oss << " fail: " << *_exception_list.begin() << std::endl;
            else
            {
                oss << std::endl;
                unsigned n = 0;
                for (elist_citr i = _exception_list.begin(); i != _exception_list.end(); i++, n++)
                    oss << "    " << n << ". " << (*i) << std::endl;
            }
        }
    }
    else
        oss << " ok" << std::endl;
    return oss.str();
}

const journal::time_ns&
test_case_result_agregation::add_test_time(const journal::time_ns& t)
{
    _test_time += t;
    return _test_time;
}

} // namespace jtt
} // namespace mrg
