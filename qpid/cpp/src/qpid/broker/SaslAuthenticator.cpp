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

#include "qpid/broker/AclModule.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/SecuritySettings.h"

#include <boost/format.hpp>

#include "config.h"

#if HAVE_SASL
#include <sys/stat.h>
#include <sasl/sasl.h>
#include <sasl/saslplug.h>
#include "qpid/sys/cyrus/CyrusSecurityLayer.h"
using qpid::sys::cyrus::CyrusSecurityLayer;
#endif

using std::string;
using namespace qpid::framing;
using qpid::sys::SecurityLayer;
using qpid::sys::SecuritySettings;
using boost::format;
using boost::str;


namespace qpid {
namespace broker {



class NullAuthenticator : public SaslAuthenticator
{
    amqp_0_10::Connection& connection;
    framing::AMQP_ClientProxy::Connection client;
    std::string realm;
    const bool encrypt;
public:
    NullAuthenticator(amqp_0_10::Connection& connection, bool encrypt);
    ~NullAuthenticator();
    void getMechanisms(framing::Array& mechanisms);
    void start(const std::string& mechanism, const std::string* response);
    void step(const std::string&) {}
    std::auto_ptr<SecurityLayer> getSecurityLayer(uint16_t maxFrameSize);
};

#if HAVE_SASL



class CyrusAuthenticator : public SaslAuthenticator
{
    sasl_conn_t *sasl_conn;
    amqp_0_10::Connection& connection;
    framing::AMQP_ClientProxy::Connection client;
    const bool encrypt;

    void processAuthenticationStep(int code, const char *challenge, unsigned int challenge_len);
    bool getUsername(std::string& uid);

public:
    CyrusAuthenticator(amqp_0_10::Connection& connection, bool encrypt);
    ~CyrusAuthenticator();
    void init();
    void getMechanisms(framing::Array& mechanisms);
    void start(const std::string& mechanism, const std::string* response);
    void step(const std::string& response);
    void getError(std::string& error);
    void getUid(std::string& uid) { getUsername(uid); }
    std::auto_ptr<SecurityLayer> getSecurityLayer(uint16_t maxFrameSize);
};

bool SaslAuthenticator::available(void) {
    return true;
}

// Called by sasl_server_init() when config file name is constructed to allow clients to verify file
// Returning SASL_FAIL here will cause sasl_server_init() to fail and an exception will be thrown
int _sasl_verifyfile_callback(void *, const char *configFileName, sasl_verify_type_t type)
{
    if (type == SASL_VRFY_CONF) {
        struct stat st;
        // verify the file exists
        if ( ::stat ( configFileName, & st) ) {
            QPID_LOG(error, "SASL: config file doesn't exist: " << configFileName);
            return SASL_FAIL;
        }
        // verify the file can be read by the broker
        if ( ::access ( configFileName, R_OK ) ) {
            QPID_LOG(error, "SASL: broker unable to read the config file. Check file permissions: " << configFileName);
            return SASL_FAIL;
        }
    }
    return SASL_OK;
}

#ifndef sasl_callback_ft
typedef int (*sasl_callback_ft)(void);
#endif

// passed to sasl_server_init()
static sasl_callback_t _callbacks[] =
{
        {   SASL_CB_VERIFYFILE, (sasl_callback_ft)&_sasl_verifyfile_callback, NULL },
    {   SASL_CB_LIST_END,   NULL,                                         NULL }
};
sasl_callback_t *callbacks = _callbacks;

// Initialize the SASL mechanism; throw if it fails.
void SaslAuthenticator::init(const std::string& saslName, std::string const & saslConfigPath )
{
    //  Check if we have a version of SASL that supports sasl_set_path()
#if (SASL_VERSION_FULL >= ((2<<16)|(1<<8)|22))
    //  If we are not given a sasl path, do nothing and allow the default to be used.
    if ( saslConfigPath.empty() ) {
        // don't pass callbacks if there is no config path
        callbacks = NULL;
        QPID_LOG ( info, "SASL: no config path set - using default." );
    }
    else {
        struct stat st;

        // Make sure the directory exists and we can read up to it.
        if ( ::stat ( saslConfigPath.c_str(), & st) ) {
          // Note: not using strerror() here because I think its messages are a little too hazy.
          if ( errno == ENOENT )
              throw Exception ( QPID_MSG ( "SASL: sasl_set_path failed: no such directory: " << saslConfigPath ) );
          if ( errno == EACCES )
              throw Exception ( QPID_MSG ( "SASL: sasl_set_path failed: cannot read parent of: " << saslConfigPath ) );
          // catch-all stat failure
          throw Exception ( QPID_MSG ( "SASL: sasl_set_path failed: cannot stat: " << saslConfigPath ) );
        }

        // Make sure that saslConfigPath is a directory.
        if (!S_ISDIR(st.st_mode)) {
            throw Exception ( QPID_MSG ( "SASL: not a directory: " << saslConfigPath ) );
        }

        // Make sure the directory is readable.
        if ( ::access ( saslConfigPath.c_str(), R_OK ) ) {
            throw Exception ( QPID_MSG ( "SASL: sasl_set_path failed: directory not readable:" << saslConfigPath ) );
        }

        // This shouldn't fail now, but check anyway.
        int code = sasl_set_path(SASL_PATH_TYPE_CONFIG, const_cast<char *>(saslConfigPath.c_str()));
        if(SASL_OK != code)
            throw Exception(QPID_MSG("SASL: sasl_set_path failed [" << code << "] " ));

        QPID_LOG(info, "SASL: config path set to " << saslConfigPath );
    }
#endif

    int code = sasl_server_init(callbacks, saslName.c_str());
    if (code != SASL_OK) {
        // TODO: Figure out who owns the char* returned by
        // sasl_errstring, though it probably does not matter much
        throw Exception(QPID_MSG("SASL: failed to parse SASL configuration file in (" << saslConfigPath << "), error: " << sasl_errstring(code, NULL, NULL)));
    }
}

void SaslAuthenticator::fini(void)
{
    sasl_done();
}

#else

typedef NullAuthenticator CyrusAuthenticator;

bool SaslAuthenticator::available(void) {
    return false;
}

void SaslAuthenticator::init(const std::string& /*saslName*/, std::string const & /*saslConfigPath*/ )
{
    throw Exception("Requested authentication but SASL unavailable");
}

void SaslAuthenticator::fini(void)
{
    return;
}

#endif

std::auto_ptr<SaslAuthenticator> SaslAuthenticator::createAuthenticator(amqp_0_10::Connection& c )
{
    if (c.getBroker().isAuthenticating()) {
        return std::auto_ptr<SaslAuthenticator>(
            new CyrusAuthenticator(c, c.getBroker().requireEncrypted()));
    } else {
        QPID_LOG(debug, "SASL: No Authentication Performed");
        return std::auto_ptr<SaslAuthenticator>(new NullAuthenticator(c, c.getBroker().requireEncrypted()));
    }
}


NullAuthenticator::NullAuthenticator(amqp_0_10::Connection& c, bool e) : connection(c), client(c.getOutput()),
                                                              realm(c.getBroker().getRealm()), encrypt(e) {}
NullAuthenticator::~NullAuthenticator() {}

void NullAuthenticator::getMechanisms(Array& mechanisms)
{
    mechanisms.add(boost::shared_ptr<FieldValue>(new Str16Value("ANONYMOUS")));
    mechanisms.add(boost::shared_ptr<FieldValue>(new Str16Value("PLAIN")));//useful for testing
}

void NullAuthenticator::start(const string& mechanism, const string* response)
{
    if (encrypt) {
        // encryption required - check to see if we are running over an
        // encrypted SSL connection.
        SecuritySettings external = connection.getExternalSecuritySettings();
        if (external.ssf < 1)    // < 1 == unencrypted
        {
            QPID_LOG(error, "Rejected un-encrypted connection.");
            throw ConnectionForcedException("Connection must be encrypted.");
        }
    }
    if (mechanism == "PLAIN") { // Old behavior
        if (response && response->size() > 0) {
            string uid;
            string::size_type i = response->find((char)0);
            if (i == 0 && response->size() > 1) {
                //no authorization id; use authentication id
                i = response->find((char)0, 1);
                if (i != string::npos) uid = response->substr(1, i-1);
            } else if (i != string::npos) {
                //authorization id is first null delimited field
                uid = response->substr(0, i);
            }//else not a valid SASL PLAIN response, throw error?
            if (!uid.empty()) {
                //append realm if it has not already been added
                i = uid.find(realm);
                if (i == string::npos || realm.size() + i < uid.size()) {
                    uid = str(format("%1%@%2%") % uid % realm);
                }
                connection.setUserId(uid);
            }
        }
    } else {
        connection.setUserId("anonymous");
    }
    AclModule* acl = connection.getBroker().getAcl();
    if (acl && !acl->approveConnection(connection))
    {
        throw ConnectionForcedException("User connection denied by configured limit");
    }
    qmf::org::apache::qpid::broker::Connection::shared_ptr cnxMgmt = connection.getMgmtObject();
    if ( cnxMgmt )
        cnxMgmt->set_saslMechanism(mechanism);

    client.tune(framing::CHANNEL_MAX, connection.getFrameMax(), 0, connection.getHeartbeatMax());
}


std::auto_ptr<SecurityLayer> NullAuthenticator::getSecurityLayer(uint16_t)
{
    std::auto_ptr<SecurityLayer> securityLayer;
    return securityLayer;
}


#if HAVE_SASL

CyrusAuthenticator::CyrusAuthenticator(amqp_0_10::Connection& c, bool _encrypt) :
    sasl_conn(0), connection(c), client(c.getOutput()), encrypt(_encrypt)
{
    init();
}

void CyrusAuthenticator::init()
{
        /*  The realm is
          * currently the most important argument below. When
          * performing authentication the user that is authenticating
          * will be looked up in a specific realm. If none is given
          * then the realm defaults to the hostname, which can cause
          * confusion when the daemon is run on different hosts that
          * may be logically sharing a realm (aka a user domain). This
          * is especially important for SASL PLAIN authentication,
          * which cannot specify a realm for the user that is
          * authenticating.
          */
    int code;

    std::string realm = connection.getBroker().getRealm();
    std::string service = connection.getBroker().getSaslServiceName();
    code = sasl_server_new(service.empty() ? BROKER_SASL_NAME : service.c_str(), /* Service name */
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
        throw ConnectionForcedException("Unable to perform authentication");
    }

    sasl_security_properties_t secprops;

    //TODO: should the actual SSF values be configurable here?
    secprops.min_ssf = encrypt ? 10: 0;
    secprops.max_ssf = 256;

    // If the transport provides encryption, notify the SASL library of
    // the key length and set the ssf range to prevent double encryption.
    SecuritySettings external = connection.getExternalSecuritySettings();
    QPID_LOG(debug, "External ssf=" << external.ssf << " and auth=" << external.authid);
    sasl_ssf_t external_ssf = (sasl_ssf_t) external.ssf;

    if ((external_ssf) && (external.authid.empty())) {
        QPID_LOG(warning, "SASL error: unable to offer EXTERNAL mechanism as authid cannot be determined");
    }

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

CyrusAuthenticator::~CyrusAuthenticator()
{
    if (sasl_conn) {
        sasl_dispose(&sasl_conn);
        sasl_conn = 0;
    }
}

void CyrusAuthenticator::getError(string& error)
{
    error = string(sasl_errdetail(sasl_conn));
}

bool CyrusAuthenticator::getUsername(string& uid)
{
    const void* ptr;

    int code = sasl_getprop(sasl_conn, SASL_USERNAME, &ptr);
    if (SASL_OK == code) {
        uid = string(const_cast<char*>(static_cast<const char*>(ptr)));
        return true;
    } else {
        QPID_LOG(warning, "Failed to retrieve sasl username");
        return false;
    }
}

void CyrusAuthenticator::getMechanisms(Array& mechanisms)
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
        throw ConnectionForcedException("Mechanism listing failed");
    } else {
        string mechanism;
        unsigned int start;
        unsigned int end;

        QPID_LOG(info, "SASL: Mechanism list: " << list);

        end = 0;
        do {
            start = end;

            // Seek to end of next mechanism
            while (end < list_len && separator[0] != list[end])
                end++;

            // Record the mechanism
            mechanisms.add(boost::shared_ptr<FieldValue>(new Str16Value(string(list, start, end - start))));
            end++;
        } while (end < list_len);
    }
}

void CyrusAuthenticator::start(const string& mechanism, const string* response)
{
    const char *challenge;
    unsigned int challenge_len;

    // This should be at same debug level as mech list in getMechanisms().
    QPID_LOG(info, "SASL: Starting authentication with mechanism: " << mechanism);
    int code = sasl_server_start(sasl_conn,
                                 mechanism.c_str(),
                                 (response ? response->c_str() : 0), (response ? response->size() : 0),
                                 &challenge, &challenge_len);

    processAuthenticationStep(code, challenge, challenge_len);
    qmf::org::apache::qpid::broker::Connection::shared_ptr cnxMgmt = connection.getMgmtObject();
    if ( cnxMgmt )
        cnxMgmt->set_saslMechanism(mechanism);
}

void CyrusAuthenticator::step(const string& response)
{
    const char *challenge;
    unsigned int challenge_len;

    int code = sasl_server_step(sasl_conn,
                            response.c_str(), response.length(),
                            &challenge, &challenge_len);

    processAuthenticationStep(code, challenge, challenge_len);
}

void CyrusAuthenticator::processAuthenticationStep(int code, const char *challenge, unsigned int challenge_len)
{
    if (SASL_OK == code) {
        std::string uid;
        if (!getUsername(uid)) {
            // TODO: Change this to an exception signaling
            // authentication failure, when one is available
            throw ConnectionForcedException("Authenticated username unavailable");
        }

        connection.setUserId(uid);

        AclModule* acl = connection.getBroker().getAcl();
        if (acl && !acl->approveConnection(connection))
        {
            throw ConnectionForcedException("User connection denied by configured limit");
        }

        QPID_LOG(info, connection.getMgmtId() << " SASL: Authentication succeeded for: " << uid);

        client.tune(framing::CHANNEL_MAX, connection.getFrameMax(), 0, connection.getHeartbeatMax());
    } else if (SASL_CONTINUE == code) {
        string challenge_str(challenge, challenge_len);

        QPID_LOG(debug, "SASL: sending challenge to client");

        client.secure(challenge_str);
    } else {
        std::string uid;
        //save error detail before trying to retrieve username as error in doing so will overwrite it
        std::string errordetail = sasl_errdetail(sasl_conn);
        if (!getUsername(uid)) {
            QPID_LOG(info, "SASL: Authentication failed (no username available yet):" << errordetail);
        } else if (SASL_NOUSER == code) {
            // SASL_NOUSER is returned when either:
            // - the user name supplied was not in the sasl db or
            // - the sasl db could not be read
            //       - because of file permissions or
            //       - because the file was not found
            QPID_LOG(info, "SASL: Authentication failed. User not found or sasldb not accessible.(" << code << ") for " << uid);
        } else {
            QPID_LOG(info, "SASL: Authentication failed for " << uid << ":" << errordetail);
        }

        // TODO: Change to more specific exceptions, when they are
        // available
        switch (code) {
        case SASL_NOMECH:
            throw ConnectionForcedException("Unsupported mechanism");
            break;
        case SASL_TRYAGAIN:
            throw ConnectionForcedException("Transient failure, try again");
            break;
        default:
            throw ConnectionForcedException("Authentication failed");
            break;
        }
    }
}

std::auto_ptr<SecurityLayer> CyrusAuthenticator::getSecurityLayer(uint16_t maxFrameSize)
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
    qmf::org::apache::qpid::broker::Connection::shared_ptr cnxMgmt = connection.getMgmtObject();
    if ( cnxMgmt )
        cnxMgmt->set_saslSsf(ssf);
    return securityLayer;
}

#endif

}}
