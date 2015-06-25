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
#include "qpid/SaslServer.h"
#include "qpid/NullSaslClient.h"
#include "qpid/NullSaslServer.h"
#include <map>
#include <string.h>

#include "config.h"

#ifndef HAVE_SASL

namespace qpid {

//Null implementation


SaslFactory::SaslFactory() {}

SaslFactory::~SaslFactory() {}

SaslFactory& SaslFactory::getInstance()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    if (!instance.get()) {
        instance = std::auto_ptr<SaslFactory>(new SaslFactory());
    }
    return *instance;
}

std::auto_ptr<Sasl> SaslFactory::create(const std::string& username, const std::string& password, const std::string&, const std::string&, int, int, bool)
{
    std::auto_ptr<Sasl> client(new NullSaslClient(username, password));
    return client;
}

std::auto_ptr<SaslServer> SaslFactory::createServer(const std::string& realm, const std::string& /*service*/, bool /*encryptionRequired*/, const qpid::sys::SecuritySettings&)
{
    std::auto_ptr<SaslServer> server(new NullSaslServer(realm));
    return server;
}

qpid::sys::Mutex SaslFactory::lock;
std::auto_ptr<SaslFactory> SaslFactory::instance;

} // namespace qpid

#else

#include "qpid/Exception.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/cyrus/CyrusSecurityLayer.h"
#include "qpid/log/Statement.h"
#include <sasl/sasl.h>
#include <strings.h>

namespace qpid {

using qpid::sys::SecurityLayer;
using qpid::sys::SecuritySettings;
using qpid::sys::cyrus::CyrusSecurityLayer;
using qpid::framing::InternalErrorException;

const size_t MAX_LOGIN_LENGTH = 50;

struct CyrusSaslSettings
{
    CyrusSaslSettings ( ) :
        username ( std::string(0) ),
        password ( std::string(0) ),
        service  ( std::string(0) ),
        host     ( std::string(0) ),
        minSsf ( 0 ),
        maxSsf ( 0 )
    {
    }

    CyrusSaslSettings ( const std::string & user, const std::string & password, const std::string & service, const std::string & host, int minSsf, int maxSsf ) :
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


class CyrusSasl : public Sasl
{
  public:
    CyrusSasl(const std::string & username, const std::string & password, const std::string & serviceName, const std::string & hostName, int minSsf, int maxSsf, bool allowInteraction);
    ~CyrusSasl();
    bool start(const std::string& mechanisms, std::string& response, const SecuritySettings* externalSettings);
    std::string step(const std::string& challenge);
    std::string getMechanism();
    std::string getUserId();
    std::auto_ptr<SecurityLayer> getSecurityLayer(uint16_t maxFrameSize);
  private:
    sasl_conn_t* conn;    
    sasl_callback_t callbacks[5];//realm, user, authname, password, end-of-list
    CyrusSaslSettings settings;
    std::string input;
    std::string mechanism;
    char login[MAX_LOGIN_LENGTH];

    /* In some contexts, like running in the broker or as a daemon, console 
     * interaction is impossible.  In those cases, we will treat the attempt 
     * to interact as an error. */
    bool allowInteraction;
    void interact(sasl_interact_t* client_interact);
};

//sasl callback functions
int getUserFromSettings(void *context, int id, const char **result, unsigned *len);
int getPasswordFromSettings(sasl_conn_t *conn, void *context, int id, sasl_secret_t **psecret);
typedef int CallbackProc();

qpid::sys::Mutex SaslFactory::lock;
std::auto_ptr<SaslFactory> SaslFactory::instance;

class CyrusSaslServer : public SaslServer
{
  public:
    CyrusSaslServer(const std::string& realm, const std::string& service, bool encryptionRequired, const qpid::sys::SecuritySettings& external);
    ~CyrusSaslServer();
    Status start(const std::string& mechanism, const std::string* response, std::string& challenge);
    Status step(const std::string* response, std::string& challenge);
    std::string getMechanisms();
    std::string getUserid();
    std::auto_ptr<qpid::sys::SecurityLayer> getSecurityLayer(size_t);
  private:
    std::string realm;
    std::string service;
    std::string userid;
    sasl_conn_t *sasl_conn;
};

SaslFactory::SaslFactory()
{
    sasl_callback_t* callbacks = 0;
    int result = sasl_client_init(callbacks);
    if (result != SASL_OK) {
        throw InternalErrorException(QPID_MSG("Sasl error: " << sasl_errstring(result, 0, 0)));
    }
}

SaslFactory::~SaslFactory()
{
    sasl_done();
}

SaslFactory& SaslFactory::getInstance()
{
    qpid::sys::Mutex::ScopedLock l(lock);
    if (!instance.get()) {
        instance = std::auto_ptr<SaslFactory>(new SaslFactory());
    }
    return *instance;
}

std::auto_ptr<Sasl> SaslFactory::create(const std::string & username, const std::string & password, const std::string & serviceName, const std::string & hostName, int minSsf, int maxSsf, bool allowInteraction)
{
    std::auto_ptr<Sasl> sasl(new CyrusSasl(username, password, serviceName, hostName, minSsf, maxSsf, allowInteraction));
    return sasl;
}

std::auto_ptr<SaslServer> SaslFactory::createServer(const std::string& realm, const std::string& service, bool encryptionRequired, const qpid::sys::SecuritySettings& external)
{
    std::auto_ptr<SaslServer> server(new CyrusSaslServer(realm, service, encryptionRequired, external));
    return server;
}

CyrusSasl::CyrusSasl(const std::string & username, const std::string & password, const std::string & serviceName, const std::string & hostName, int minSsf, int maxSsf, bool allowInteraction)
    : conn(0), settings(username, password, serviceName, hostName, minSsf, maxSsf), allowInteraction(allowInteraction)
{
    size_t i = 0;

    callbacks[i].id = SASL_CB_GETREALM;
    callbacks[i].proc = 0;
    callbacks[i++].context = 0;

    if (!settings.username.empty()) {
        callbacks[i].id = SASL_CB_AUTHNAME;
        callbacks[i].proc = (CallbackProc*) &getUserFromSettings;
        callbacks[i++].context = &settings;

        callbacks[i].id = SASL_CB_PASS;
        if (settings.password.empty()) {
            callbacks[i].proc = 0;
            callbacks[i++].context = 0;        
        } else {
            callbacks[i].proc = (CallbackProc*) &getPasswordFromSettings;
            callbacks[i++].context = &settings;
        }
    }


    callbacks[i].id = SASL_CB_LIST_END;
    callbacks[i].proc = 0;
    callbacks[i++].context = 0;
}

CyrusSasl::~CyrusSasl() 
{
    if (conn) {
        sasl_dispose(&conn);
    }
}

namespace {
    const std::string SSL("ssl");
}

bool CyrusSasl::start(const std::string& mechanisms, std::string& response, const SecuritySettings* externalSettings)
{
    QPID_LOG(debug, "CyrusSasl::start(" << mechanisms << ")");
    int result = sasl_client_new(settings.service.c_str(),
                                 settings.host.c_str(),
                                 0, 0, /* Local and remote IP address strings */
                                 callbacks,
                                 0,          /* security flags */
                                 &conn);
    
    if (result != SASL_OK) throw InternalErrorException(QPID_MSG("Sasl error: " << sasl_errdetail(conn)));

    sasl_security_properties_t secprops;

    if (externalSettings) {
        sasl_ssf_t external_ssf = (sasl_ssf_t) externalSettings->ssf;
        if (external_ssf) {
            int result = sasl_setprop(conn, SASL_SSF_EXTERNAL, &external_ssf);
            if (result != SASL_OK) {
                throw framing::InternalErrorException(QPID_MSG("SASL error: unable to set external SSF: " << result));
            }
            QPID_LOG(debug, "external SSF detected and set to " << external_ssf);
        }
        if (externalSettings->authid.size()) {
            const char* external_authid = externalSettings->authid.c_str();
            result = sasl_setprop(conn, SASL_AUTH_EXTERNAL, external_authid);
            if (result != SASL_OK) {
                throw framing::InternalErrorException(QPID_MSG("SASL error: unable to set external auth: " << result));
            }
            QPID_LOG(debug, "external auth detected and set to " << external_authid);
        }
    }

    secprops.min_ssf = settings.minSsf;
    secprops.max_ssf = settings.maxSsf;
    secprops.maxbufsize = 65535;

    QPID_LOG(debug, "min_ssf: " << secprops.min_ssf << ", max_ssf: " << secprops.max_ssf);

    secprops.property_names = 0;
    secprops.property_values = 0;
    secprops.security_flags = 0;//TODO: provide means for application to configure these

    result = sasl_setprop(conn, SASL_SEC_PROPS, &secprops);
    if (result != SASL_OK) {
        throw framing::InternalErrorException(QPID_MSG("SASL error: " << sasl_errdetail(conn)));
    }

    sasl_interact_t* client_interact = 0;
    const char *out = 0;
    unsigned outlen = 0;
    const char *chosenMechanism = 0;

    do {        
        result = sasl_client_start(conn,
                                   mechanisms.c_str(),
                                   &client_interact,
                                   &out,
                                   &outlen,
                                   &chosenMechanism);
        
        if (result == SASL_INTERACT) {
            interact(client_interact);
        }        
    } while (result == SASL_INTERACT);

    if (result == SASL_NOMECH) {
        if (mechanisms.size()) {
            throw qpid::Exception(std::string("Can't authenticate using ") + mechanisms);
        } else {
            throw qpid::Exception("No mutually acceptable authentication mechanism");
        }
    } else if (result != SASL_CONTINUE && result != SASL_OK) {
        throw InternalErrorException(QPID_MSG("Sasl error: " << sasl_errdetail(conn)));
    }

    mechanism = std::string(chosenMechanism);
    QPID_LOG(debug, "CyrusSasl::start(" << mechanisms << "): selected "
             << mechanism << " response: '" << std::string(out, outlen) << "'");
    if (out) {
        response = std::string(out, outlen);
        return true;
    } else {
        return false;
    }
}

std::string CyrusSasl::step(const std::string& challenge)
{
    sasl_interact_t* client_interact = 0;
    const char *out = 0;
    unsigned outlen = 0;
    int result = 0;
    do {
        result = sasl_client_step(conn,  /* our context */
                                  challenge.data(), /* the data from the server */
                                  challenge.size(), /* it's length */
                                  &client_interact,  /* this should be
                                                        unallocated and NULL */
                                  &out,     /* filled in on success */
                                  &outlen); /* filled in on success */
        
        if (result == SASL_INTERACT) {
            interact(client_interact);
        }        
    } while (result == SASL_INTERACT);

    std::string response;
    if (result == SASL_CONTINUE || result == SASL_OK) response = std::string(out, outlen);
    else if (result != SASL_OK) {
        throw InternalErrorException(QPID_MSG("Sasl error: " << sasl_errdetail(conn)));
    }
    QPID_LOG(debug, "CyrusSasl::step(" << challenge << "): " << response);
    return response;
}

std::string CyrusSasl::getMechanism()
{
    return mechanism;
}

std::string CyrusSasl::getUserId()
{
    int propResult;
    const void* operName;

    propResult = sasl_getprop(conn, SASL_USERNAME, &operName);
    if (propResult == SASL_OK)
        return std::string((const char*) operName);

    return std::string();
}

void CyrusSasl::interact(sasl_interact_t* client_interact)
{

    /*
      In some context console interaction cannot be allowed, such
      as when this code run as part of a broker, or as a some other 
      daemon.   In those cases we will treat the attempt to 
    */
    if ( ! allowInteraction ) {
        throw InternalErrorException("interaction disallowed");
    }

    if (client_interact->id == SASL_CB_PASS) {
        char* password = getpass(client_interact->prompt);
        input = std::string(password);
        client_interact->result = input.data();
        client_interact->len = input.size();
    } else {
        std::cout << client_interact->prompt;
        if (client_interact->defresult) std::cout << " (" << client_interact->defresult << ")";
        std::cout << ": ";
        if (std::cin >> input) {
            client_interact->result = input.data();
            client_interact->len = input.size();
        }
    }

}

std::auto_ptr<SecurityLayer> CyrusSasl::getSecurityLayer(uint16_t maxFrameSize)
{
    const void* value(0);
    int result = sasl_getprop(conn, SASL_SSF, &value);
    if (result != SASL_OK) {
        throw framing::InternalErrorException(QPID_MSG("SASL error: " << sasl_errdetail(conn)));
    }
    uint ssf = *(reinterpret_cast<const unsigned*>(value));
    std::auto_ptr<SecurityLayer> securityLayer;
    if (ssf) {
        QPID_LOG(info, "Installing security layer,  SSF: "<< ssf);
        securityLayer = std::auto_ptr<SecurityLayer>(new CyrusSecurityLayer(conn, maxFrameSize, ssf));
    }
    return securityLayer;
}

CyrusSaslServer::CyrusSaslServer(const std::string& r, const std::string& s, bool encryptionRequired, const qpid::sys::SecuritySettings& external) : realm(r), service(s), sasl_conn(0)
{
    int code = sasl_server_new(service.c_str(), /* Service name */
                               NULL, /* Server FQDN, gethostname() */
                               realm.c_str(), /* Authentication realm */
                               NULL, /* Local IP, needed for some mechanism */
                               NULL, /* Remote IP, needed for some mechanism */
                               NULL, /* Callbacks */
                               0, /* Connection flags */
                               &sasl_conn);

    if (SASL_OK != code) {
        QPID_LOG(error, "SASL: Connection creation failed: [" << code << "] " << sasl_errdetail(sasl_conn));

        // TODO: Change this to an exception signaling
        // server error, when one is available
        throw qpid::framing::ConnectionForcedException("Unable to perform authentication");
    }

    sasl_security_properties_t secprops;

    //TODO: should the actual SSF values be configurable here?
    secprops.min_ssf = encryptionRequired ? 10: 0;
    secprops.max_ssf = 256;

    // If the transport provides encryption, notify the SASL library of
    // the key length and set the ssf range to prevent double encryption.
    QPID_LOG(debug, "External ssf=" << external.ssf << " and auth=" << external.authid);
    sasl_ssf_t external_ssf = (sasl_ssf_t) external.ssf;
    if (external_ssf) {
        int result = sasl_setprop(sasl_conn, SASL_SSF_EXTERNAL, &external_ssf);
        if (result != SASL_OK) {
            throw framing::InternalErrorException(QPID_MSG("SASL error: unable to set external SSF: " << result));
        }

        secprops.max_ssf = secprops.min_ssf = 0;
    }

    QPID_LOG(debug, "min_ssf: " << secprops.min_ssf <<
             ", max_ssf: " << secprops.max_ssf <<
             ", external_ssf: " << external_ssf );

    if (!external.authid.empty()) {
        const char* external_authid = external.authid.c_str();
        int result = sasl_setprop(sasl_conn, SASL_AUTH_EXTERNAL, external_authid);
        if (result != SASL_OK) {
            throw framing::InternalErrorException(QPID_MSG("SASL error: unable to set external auth: " << result));
        }

        QPID_LOG(debug, "external auth detected and set to " << external_authid);
    }
    secprops.maxbufsize = 65535;
    secprops.property_names = 0;
    secprops.property_values = 0;
    secprops.security_flags = 0; /* or SASL_SEC_NOANONYMOUS etc as appropriate */
    /*
     * The nodict flag restricts SASL authentication mechanisms
     * to those that are not susceptible to dictionary attacks.
     * They are:
     *   SRP
     *   PASSDSS-3DES-1
     *   EXTERNAL
     */
    if (external.nodict) secprops.security_flags |= SASL_SEC_NODICTIONARY;
    int result = sasl_setprop(sasl_conn, SASL_SEC_PROPS, &secprops);
    if (result != SASL_OK) {
        throw framing::InternalErrorException(QPID_MSG("SASL error: " << result));
    }
}

CyrusSaslServer::~CyrusSaslServer()
{
    if (sasl_conn) {
        sasl_dispose(&sasl_conn);
        sasl_conn = 0;
    }
}

CyrusSaslServer::Status CyrusSaslServer::start(const std::string& mechanism, const std::string* response, std::string& chllng)
{
    const char *challenge;
    unsigned int challenge_len;

    // This should be at same debug level as mech list in getMechanisms().
    QPID_LOG(info, "SASL: Starting authentication with mechanism: " << mechanism);
    int code = sasl_server_start(sasl_conn,
                                 mechanism.c_str(),
                                 (response ? response->c_str() : 0), (response ? response->size() : 0),
                                 &challenge, &challenge_len);
    switch (code) {
      case SASL_OK:
        return SaslServer::OK;
      case SASL_CONTINUE:
        chllng = std::string(challenge, challenge_len);
        return SaslServer::CHALLENGE;
      case SASL_NOMECH:
        QPID_LOG(info, "Unsupported mechanism: " << mechanism);
      default:
        return SaslServer::FAIL;
    }
}

CyrusSaslServer::Status CyrusSaslServer::step(const std::string* response, std::string& chllng)
{
    const char *challenge;
    unsigned int challenge_len;

    int code = sasl_server_step(sasl_conn,
                                (response ? response->c_str() : 0), (response ? response->size() : 0),
                                &challenge, &challenge_len);

    switch (code) {
      case SASL_OK:
        return SaslServer::OK;
      case SASL_CONTINUE:
        chllng = std::string(challenge, challenge_len);
        return SaslServer::CHALLENGE;
      default:
        return SaslServer::FAIL;
    }

}
std::string CyrusSaslServer::getMechanisms()
{
    const char *separator = " ";
    const char *list;
    unsigned int list_len;
    int count;

    int code = sasl_listmech(sasl_conn, NULL,
                             "", separator, "",
                             &list, &list_len,
                             &count);

    if (SASL_OK != code) {
        QPID_LOG(info, "SASL: Mechanism listing failed: " << sasl_errdetail(sasl_conn));

        // TODO: Change this to an exception signaling
        // server error, when one is available
        throw qpid::framing::ConnectionForcedException("Mechanism listing failed");
    } else {
        std::string mechanisms(list, list_len);
        QPID_LOG(info, "SASL: Mechanism list: " << mechanisms);
        return mechanisms;
    }
}
std::string CyrusSaslServer::getUserid()
{
    const void* ptr;
    int code = sasl_getprop(sasl_conn, SASL_USERNAME, &ptr);
    if (SASL_OK == code) {
        userid = static_cast<const char*>(ptr);
    } else {
        QPID_LOG(warning, "Failed to retrieve sasl username");
    }
    return userid;
}

std::auto_ptr<SecurityLayer> CyrusSaslServer::getSecurityLayer(size_t maxFrameSize)
{
    const void* value(0);
    int result = sasl_getprop(sasl_conn, SASL_SSF, &value);
    if (result != SASL_OK) {
        throw framing::InternalErrorException(QPID_MSG("SASL error: " << sasl_errdetail(sasl_conn)));
    }
    uint ssf = *(reinterpret_cast<const unsigned*>(value));
    std::auto_ptr<SecurityLayer> securityLayer;
    if (ssf) {
        securityLayer = std::auto_ptr<SecurityLayer>(new CyrusSecurityLayer(sasl_conn, maxFrameSize, ssf));
    }
    return securityLayer;
}

int getUserFromSettings(void* context, int /*id*/, const char** result, unsigned* /*len*/)
{
    if (context) {
        *result = ((CyrusSaslSettings*) context)->username.c_str();
        QPID_LOG(debug, "getUserFromSettings(): " << (*result));
        return SASL_OK;
    } else {
        return SASL_FAIL;
    }
}

namespace {
// Global map of secrets allocated for SASL connections via callback
// to getPasswordFromSettings. Ensures secrets are freed.
class SecretsMap {
    typedef std::map<sasl_conn_t*, void*> Map;
    Map map;
    sys::Mutex lock;
  public:
    void keep(sasl_conn_t* conn, void* secret) {
        sys::Mutex::ScopedLock l(lock);
        Map::iterator i = map.find(conn);
        if (i != map.end()) free(i->second);
        map[conn] = secret;
    }

    ~SecretsMap() {
        for (Map::iterator i = map.begin(); i != map.end(); ++i)
            free(i->second);
    }
};
SecretsMap getPasswordFromSettingsSecrets;
}

int getPasswordFromSettings(sasl_conn_t* conn, void* context, int /*id*/, sasl_secret_t** psecret)
{
    if (context) {
        size_t length = ((CyrusSaslSettings*) context)->password.size();
        sasl_secret_t* secret = (sasl_secret_t*) malloc(sizeof(sasl_secret_t) + length);
        getPasswordFromSettingsSecrets.keep(conn, secret);
        secret->len = length;
        memcpy(secret->data, ((CyrusSaslSettings*) context)->password.data(), length);
        *psecret = secret;
        return SASL_OK;
    } else {
        return SASL_FAIL;
    }
}
} // namespace qpid

#endif
