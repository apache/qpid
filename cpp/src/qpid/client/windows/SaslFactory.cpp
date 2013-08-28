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

#include "qpid/SaslFactory.h"

#include "qpid/Exception.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/log/Statement.h"
#include "qpid/NullSaslServer.h"

#include "boost/tokenizer.hpp"

namespace qpid {

using qpid::sys::SecurityLayer;
using qpid::sys::SecuritySettings;
using qpid::framing::InternalErrorException;

struct WindowsSaslSettings
{
    WindowsSaslSettings ( ) :
        username ( std::string(0) ),
        password ( std::string(0) ),
        service  ( std::string(0) ),
        host     ( std::string(0) ),
        minSsf ( 0 ),
        maxSsf ( 0 )
    {
    }

    WindowsSaslSettings ( const std::string & user, const std::string & password, const std::string & service, const std::string & host, int minSsf, int maxSsf ) :
        username(user),
        password(password),
        service(service),
        host(host),
        minSsf(minSsf),
        maxSsf(maxSsf)
    {
    }

    std::string username,
                password,
                service,
                host;

    int minSsf,
        maxSsf;
};

class WindowsSasl : public Sasl
{
  public:
    WindowsSasl( const std::string &, const std::string &, const std::string &, const std::string &, int, int );
    ~WindowsSasl();
    bool start(const std::string& mechanisms, std::string& response, const SecuritySettings* externalSettings);
    std::string step(const std::string& challenge);
    std::string getMechanism();
    std::string getUserId();
    std::auto_ptr<SecurityLayer> getSecurityLayer(uint16_t maxFrameSize);
  private:
    WindowsSaslSettings settings;
    std::string mechanism;
};

qpid::sys::Mutex SaslFactory::lock;
std::auto_ptr<SaslFactory> SaslFactory::instance;

SaslFactory::SaslFactory()
{
}

SaslFactory::~SaslFactory()
{
}

SaslFactory& SaslFactory::getInstance()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    if (!instance.get()) {
        instance = std::auto_ptr<SaslFactory>(new SaslFactory());
    }
    return *instance;
}

std::auto_ptr<Sasl> SaslFactory::create( const std::string & username, const std::string & password, const std::string & serviceName, const std::string & hostName, int minSsf, int maxSsf, bool )
{
    std::auto_ptr<Sasl> sasl(new WindowsSasl( username, password, serviceName, hostName, minSsf, maxSsf ));
    return sasl;
}

std::auto_ptr<SaslServer> SaslFactory::createServer( const std::string& realm, bool /*encryptionRequired*/, const qpid::sys::SecuritySettings& )
{
    std::auto_ptr<SaslServer> server(new NullSaslServer(realm));
    return server;
}

namespace {
    const std::string ANONYMOUS = "ANONYMOUS";
    const std::string PLAIN = "PLAIN";
}

WindowsSasl::WindowsSasl( const std::string & username, const std::string & password, const std::string & serviceName, const std::string & hostName, int minSsf, int maxSsf )
  : settings(username, password, serviceName, hostName, minSsf, maxSsf) 
{
}

WindowsSasl::~WindowsSasl() 
{
}

bool WindowsSasl::start(const std::string& mechanisms, std::string& response,
                        const SecuritySettings* /*externalSettings*/)
{
    QPID_LOG(debug, "WindowsSasl::start(" << mechanisms << ")");

    typedef boost::tokenizer<boost::char_separator<char> > tokenizer;
    boost::char_separator<char> sep(" ");
    bool havePlain = false;
    bool haveAnon = false;
    tokenizer mechs(mechanisms, sep);
    for (tokenizer::iterator mech = mechs.begin();
         mech != mechs.end();
         ++mech) {
        if (*mech == ANONYMOUS)
            haveAnon = true;
        else if (*mech == PLAIN)
            havePlain = true;
    }
    if (!haveAnon && !havePlain)
        throw InternalErrorException(QPID_MSG("Sasl error: no common mechanism"));

    if (havePlain) {
        mechanism = PLAIN;
        response = ((char)0) + settings.username + ((char)0) + settings.password;
    }
    else {
        mechanism = ANONYMOUS;
        response = "";
    }
    return true;
}

std::string WindowsSasl::step(const std::string& /*challenge*/)
{
    // Shouldn't get this for PLAIN...
    throw InternalErrorException(QPID_MSG("Sasl step error"));
}

std::string WindowsSasl::getMechanism()
{
    return mechanism;
}

std::string WindowsSasl::getUserId()
{
    return std::string(); // TODO - when GSSAPI is supported, return userId for connection.
}

std::auto_ptr<SecurityLayer> WindowsSasl::getSecurityLayer(uint16_t /*maxFrameSize*/)
{
    return std::auto_ptr<SecurityLayer>(0);
}

} // namespace qpid
