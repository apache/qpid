/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <iostream>
#include <memory>
#include "apr_signal.h"

#include "Acceptor.h"
#include "Configuration.h"
#include "QpidError.h"
#include "SessionHandlerFactoryImpl.h"

//optional includes:
#ifdef _USE_APR_IO_

#include "BlockingAPRAcceptor.h"
#include "LFAcceptor.h"

#endif 

using namespace qpid::broker;
using namespace qpid::io;

void handle_signal(int signal);

Acceptor* createAcceptor(Configuration& config);

int main(int argc, char** argv)
{
    SessionHandlerFactoryImpl factory;
    Configuration config;
    try{

        config.parse(argc, argv);
        if(config.isHelp()){
            config.usage();
        }else{
#ifdef _USE_APR_IO_         
            apr_signal(SIGINT, handle_signal);
#endif
            try{    
                std::auto_ptr<Acceptor> acceptor(createAcceptor(config));
                try{             
                    acceptor->bind(config.getPort(), &factory);
                }catch(qpid::QpidError error){
                    std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
                }
            }catch(qpid::QpidError error){
                std::cout << "Error [" << error.code << "] " << error.msg << " (" << error.file << ":" << error.line << ")" << std::endl;
            }
        }            
    }catch(Configuration::ParseException error){
        std::cout << "Error: " << error.error << std::endl;        
    }

    return 1;
}

Acceptor* createAcceptor(Configuration& config){
    const string type(config.getAcceptor());
#ifdef _USE_APR_IO_         
    if("blocking" == type){
        std::cout << "Using blocking acceptor " << std::endl;
        return new BlockingAPRAcceptor(config.isTrace(), config.getConnectionBacklog());
    }else if("non-blocking" == type){
        std::cout << "Using non-blocking acceptor " << std::endl;
        return new LFAcceptor(config.isTrace(), 
                              config.getConnectionBacklog(), 
                              config.getWorkerThreads(),
                              config.getMaxConnections());
    }
#endif
    throw Configuration::ParseException("Unrecognised acceptor: " + type);
}

void handle_signal(int /*signal*/){
    std::cout << "Shutting down..." << std::endl;
}
