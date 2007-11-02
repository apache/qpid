#ifndef _TestOptions_
#define _TestOptions_
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

#include <CommonOptions.h>

namespace qpid {

struct TestOptions : public qpid::CommonOptions
{
    TestOptions() : desc("Options"), broker("localhost"), virtualhost(""), clientid("cpp"), help(false)
    {
        using namespace qpid::program_options;
        using namespace boost::program_options;
        CommonOptions::addTo(desc);        
        desc.add_options()
            ("broker,b", optValue(broker, "HOSTNAME"), "the hostname to connect to")
            ("virtualhost,v", optValue(virtualhost, "VIRTUAL_HOST"), "virtual host")
            ("clientname,n", optValue(clientid, "ID"), "unique client identifier")
            ("help,h", optValue(help), "print this usage statement");
    }

    void parse(int argc, char** argv)
    {
        using namespace boost::program_options;
        try {
            variables_map vm;
            store(parse_command_line(argc, argv, desc), vm);
            notify(vm);
        } catch(const error& e) {
            std::cerr << "Error: " << e.what() << std::endl
                      << "Specify '--help' for usage." << std::endl;
        }
    }

    void usage()
    {
        std::cout << desc << std::endl; 
    }

    boost::program_options::options_description desc;
    std::string broker;      
    std::string virtualhost;
    std::string clientid;            
    bool help;
};

}

#endif
