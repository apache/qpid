#ifndef _Broker_
#define _Broker_

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
#include <SessionHandlerFactoryImpl.h>
#include <sys/Runnable.h>
#include <sys/Acceptor.h>
#include <SharedObject.h>

namespace qpid {
namespace broker {
/**
 * A broker instance. 
 */
class Broker : public qpid::sys::Runnable,
               public qpid::SharedObject<Broker>
{
  public:
    struct Options : public CommonOptions {
        Options();
        void addTo(po::options_description&);
        int workerThreads;
        int maxConnections;
        int connectionBacklog;
        std::string store;      
        long stagingThreshold;
    };
            
    virtual ~Broker();

    /**
     * Create a broker.
     * @param port Port to listen on or 0 to pick a port dynamically.
     */
    static shared_ptr create(int16_t port = Options::DEFAULT_PORT);

    /**
     * Create a broker using a Configuration.
     */
    static shared_ptr create(const Options& config);

    /**
     * Return listening port. If called before bind this is
     * the configured port. If called after it is the actual
     * port, which will be different if the configured port is
     * 0.
     */
    virtual int16_t getPort() const { return acceptor->getPort(); }
            
    /**
     * Run the broker. Implements Runnable::run() so the broker
     * can be run in a separate thread.
     */
    virtual void run();

    /** Shut down the broker */
    virtual void shutdown();

  private:
    Broker(const Options& config);
    Options config;
    qpid::sys::Acceptor::shared_ptr acceptor;
    SessionHandlerFactoryImpl factory;
};
}
}
            


#endif  /*!_Broker_*/
