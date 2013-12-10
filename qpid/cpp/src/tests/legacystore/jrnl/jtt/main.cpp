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

#include "test_mgr.h"

#include "args.h"
#include <csignal>
#include <iostream>

#define PACKAGE_NAME "Journal Test Tool"
#define VERSION "0.1"

namespace po = boost::program_options;

int main(int argc, char** argv)
{
    std::signal(SIGINT, mrg::jtt::test_mgr::signal_handler);
    std::signal(SIGTERM, mrg::jtt::test_mgr::signal_handler);

    std::cout << PACKAGE_NAME << " v." << VERSION << std::endl;

    std::ostringstream oss;
    oss << PACKAGE_NAME << " options";
    mrg::jtt::args args(oss.str());
    if (args.parse(argc, argv)) return 1;

    try
    {
        mrg::jtt::test_mgr tm(args);
        tm.run();
        if (tm.error()) return 2; // One or more tests threw exceptions
    }
    catch (const std::exception& e)
    {
        std::cerr << e.what() << std::endl;
        return 3;
    }
    return 0;
}
