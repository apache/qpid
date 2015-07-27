
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

#include "qpid/broker/ConnectionHandler.h"

#include "qpid/SaslFactory.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/broker/SecureConnection.h"
#include "qpid/Url.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/framing/ConnectionStartOkBody.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/sys/Time.h"
#include "qpid/broker/AclModule.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qmf/org/apache/qpid/broker/EventClientConnectFail.h"
#include "qpid/Version.h"

using namespace qpid;
using namespace qpid::broker;

using std::string;

using namespace qpid::framing;
using qpid::sys::SecurityLayer;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace
{
const std::string en_US     = "en_US";
const std::string QPID_FED_LINK = "qpid.fed_link";
const std::string QPID_FED_TAG  = "qpid.federation_tag";
const std::string CLIENT_PROCESS_NAME("qpid.client_process");
const std::string CLIENT_PID("qpid.client_pid");
const std::string CLIENT_PPID("qpid.client_ppid");
const std::string SPACE(" ");
}

void ConnectionHandler::close(connection::CloseCode code, const string& text)
{
    handler->proxy.close(code, text);
}

void ConnectionHandler::heartbeat()
{
    handler->proxy.heartbeat();
}

bool ConnectionHandler::handle(const framing::AMQMethodBody& method)
{
    //Need special handling for start-ok, in order to distinguish
    //between null and empty response
    if (method.isA<ConnectionStartOkBody>()) {
        handler->startOk(dynamic_cast<const ConnectionStartOkBody&>(method));
        return true;
    } else {
        return invoke(static_cast<AMQP_AllOperations::ConnectionHandler&>(*handler), method);
    }
}

void ConnectionHandler::handle(framing::AMQFrame& frame)
{
    AMQMethodBody* method=frame.getBody()->getMethod();
    try{
        if (method && handle(*method)) {
            // This is a connection control frame, nothing more to do.
        } else if (isOpen()) {
            handler->connection.getChannel(frame.getChannel()).in(frame);
        } else {
            handler->connection.close(
                connection::CLOSE_CODE_FRAMING_ERROR,
                "Connection not yet open, invalid frame received.");
        }
    }catch(ConnectionException& e){
        handler->connection.close(e.code, e.what());
    }catch(std::exception& e){
        handler->connection.close(connection::CLOSE_CODE_CONNECTION_FORCED, e.what());
    }
}

void ConnectionHandler::setSecureConnection(SecureConnection* secured)
{
    handler->secured = secured;
}

ConnectionHandler::ConnectionHandler(qpid::broker::amqp_0_10::Connection& connection, bool isClient)  :
    handler(new Handler(connection, isClient)) {}

ConnectionHandler::Handler::Handler(qpid::broker::amqp_0_10::Connection& c, bool isClient) :
    proxy(c.getOutput()),
    connection(c), serverMode(!isClient), secured(0),
    isOpen(false)
{
    if (serverMode) {
        FieldTable properties;
        Array mechanisms(0x95);
        boost::shared_ptr<const System> sysInfo = connection.getBroker().getSystem();

        properties.setString("product", qpid::product);
        properties.setString("version", qpid::version);
        if (sysInfo) {
            properties.setString("platform", sysInfo->getOsName());
            properties.setString("host", sysInfo->getNodeName());
        }
        properties.setString(QPID_FED_TAG, connection.getBroker().getFederationTag());

        authenticator = SaslAuthenticator::createAuthenticator(c);
        authenticator->getMechanisms(mechanisms);

        Array locales(0x95);
        boost::shared_ptr<FieldValue> l(new Str16Value(en_US));
        locales.add(l);
        proxy.start(properties, mechanisms, locales);
    }

    maxFrameSize = (64 * 1024) - 1;
}


ConnectionHandler::Handler::~Handler() {}


void ConnectionHandler::Handler::startOk(const framing::FieldTable& /*clientProperties*/,
                                         const string& /*mechanism*/,
                                         const string& /*response*/,
                                         const string& /*locale*/)
{
    //Need special handling for start-ok, in order to distinguish
    //between null and empty response -> should never use this method
    assert(false);
}

void ConnectionHandler::Handler::startOk(const ConnectionStartOkBody& body)
{
    const framing::FieldTable& clientProperties = body.getClientProperties();
    qmf::org::apache::qpid::broker::Connection::shared_ptr mgmtObject = connection.getMgmtObject();
    types::Variant::Map properties;
    qpid::amqp_0_10::translate(clientProperties, properties);

    if (mgmtObject != 0) {
        string procName = clientProperties.getAsString(CLIENT_PROCESS_NAME);
        uint32_t pid = clientProperties.getAsInt(CLIENT_PID);
        uint32_t ppid = clientProperties.getAsInt(CLIENT_PPID);

        mgmtObject->set_remoteProperties(properties);
        if (!procName.empty())
            mgmtObject->set_remoteProcessName(procName);
        if (pid != 0)
            mgmtObject->set_remotePid(pid);
        if (ppid != 0)
            mgmtObject->set_remoteParentPid(ppid);
    }
    try {
        authenticator->start(body.getMechanism(), body.hasResponse() ? &body.getResponse() : 0);
    } catch (std::exception& /*e*/) {
        management::ManagementAgent* agent = connection.getAgent();
        bool logEnabled;
        QPID_LOG_TEST_CAT(debug, model, logEnabled);
        if (logEnabled || agent)
        {
            string error;
            string uid;
            authenticator->getError(error);
            authenticator->getUid(uid);
            if (agent && mgmtObject) {
                agent->raiseEvent(_qmf::EventClientConnectFail(connection.getMgmtId(), uid, error,
                                                               mgmtObject->get_remoteProperties()));
            }
            QPID_LOG_CAT(debug, model, "Failed connection. rhost:" << connection.getMgmtId()
                << " user:" << uid
                << " reason:" << error );
        }
        throw;
    }

    connection.setClientProperties(properties);
    if (clientProperties.isSet(QPID_FED_TAG)) {
        connection.setFederationPeerTag(clientProperties.getAsString(QPID_FED_TAG));
    }
}

void ConnectionHandler::Handler::secureOk(const string& response)
{
    try {
        authenticator->step(response);
    } catch (std::exception& /*e*/) {
        management::ManagementAgent* agent = connection.getAgent();
        bool logEnabled;
        QPID_LOG_TEST_CAT(debug, model, logEnabled);
        if (logEnabled || agent)
        {
            string error;
            string uid;
            authenticator->getError(error);
            authenticator->getUid(uid);
            if (agent && connection.getMgmtObject()) {
                agent->raiseEvent(_qmf::EventClientConnectFail(connection.getMgmtId(), uid, error,
                                                               connection.getMgmtObject()->get_remoteProperties()));
            }
            QPID_LOG_CAT(debug, model, "Failed connection. rhost:" << connection.getMgmtId()
                << " user:" << uid
                << " reason:" << error );
        }
        throw;
    }
}

void ConnectionHandler::Handler::tuneOk(uint16_t /*channelmax*/,
    uint16_t framemax, uint16_t heartbeat)
{
    if (framemax) connection.setFrameMax(framemax);
    connection.setHeartbeatInterval(heartbeat);
}

void ConnectionHandler::Handler::open(const string& /*virtualHost*/,
                                      const framing::Array& /*capabilities*/, bool /*insist*/)
{
    if (connection.getUserId().empty() && connection.getBroker().isAuthenticating()) {
        throw ConnectionForcedException("Not authenticated!");
    }

    if (connection.isFederationLink()) {
        AclModule* acl =  connection.getBroker().getAcl();
        if (acl && acl->userAclRules()) {
            if (!acl->authorise(connection.getUserId(),acl::ACT_CREATE,acl::OBJ_LINK,"")){
                connection.close(framing::connection::CLOSE_CODE_CONNECTION_FORCED,
                                 QPID_MSG("ACL denied " << connection.getUserId()
                                          << " creating a federation link"));
                return;
            }
        } else {
            if (connection.getBroker().isAuthenticating()) {
                connection.close(framing::connection::CLOSE_CODE_CONNECTION_FORCED,
                                 QPID_MSG("User " << connection.getUserId()
                                          << " federation connection denied. Systems with authentication "
                                          "enabled must specify ACL create link rules."));
                return;
            }
        }
        QPID_LOG(info, "Connection is a federation link");
    }
    std::vector<Url> urls = connection.getBroker().getKnownBrokers();
    framing::Array array(0x95); // str16 array
    for (std::vector<Url>::iterator i = urls.begin(); i < urls.end(); ++i)
        array.add(boost::shared_ptr<Str16Value>(new Str16Value(i->str())));

    //install security layer if one has been negotiated:
    if (secured) {
        std::auto_ptr<SecurityLayer> sl = authenticator->getSecurityLayer(connection.getFrameMax());
        if (sl.get()) secured->activateSecurityLayer(sl);
    }

    isOpen = true;
    proxy.openOk(array);
}


void ConnectionHandler::Handler::close(uint16_t replyCode, const string& replyText)
{
    if (replyCode != 200) {
        QPID_LOG(warning, "Client closed connection with " << replyCode << ": " << replyText);
    }

    if (replyCode == framing::connection::CLOSE_CODE_CONNECTION_FORCED)
        connection.notifyConnectionForced(replyText);

    proxy.closeOk();
    connection.getOutput().close();
}

void ConnectionHandler::Handler::closeOk(){
    connection.getOutput().close();
}

void ConnectionHandler::Handler::heartbeat(){
    // For general case, do nothing - the purpose of heartbeats is
    // just to make sure that there is some traffic on the connection
    // within the heart beat interval, we check for the traffic and
    // don't need to do anything in response to heartbeats.  The
    // exception is when we are in fact the client to another broker
    // (i.e. an inter-broker link), in which case we echo the
    // heartbeat back to the peer
    if (!serverMode) proxy.heartbeat();
}

void ConnectionHandler::Handler::start(const FieldTable& serverProperties,
                                       const framing::Array& supportedMechanisms,
                                       const framing::Array& /*locales*/)
{
    if (serverMode) {
        throw ConnectionForcedException("Invalid protocol sequence.");
    }


    string requestedMechanism = connection.getAuthMechanism();

    std::string username = connection.getUsername();

    std::string password = connection.getPassword();
    std::string host     = connection.getHost();
    std::string service("qpidd");

    if ( connection.getBroker().isAuthenticating() ) {
        sasl = SaslFactory::getInstance().create( username,
                                                  password,
                                                  service,
                                                  host,
                                                  0,   // TODO -- mgoulish Fri Sep 24 2010
                                                  256,
                                                  false ); // disallow interaction
    }
    std::string supportedMechanismsList;
    Array::const_iterator i;

    /*
      If no specific mechanism has been requested, just make
      a list of all of them, and assert that the one the caller
      requested is there.  ( If *any* are supported! )
    */
    if ( requestedMechanism.empty() ) {
        for ( i = supportedMechanisms.begin(); i != supportedMechanisms.end(); ++i) {
            if (i != supportedMechanisms.begin())
                supportedMechanismsList += SPACE;
            supportedMechanismsList += (*i)->get<std::string>();
        }
    }
    else {
        /*
          The caller has requested a mechanism.  If it's available,
          make sure it ends up at the head of the list.
        */
        for ( i = supportedMechanisms.begin(); i != supportedMechanisms.end(); ++i) {
            string currentMechanism = (*i)->get<std::string>();

            if ( requestedMechanism == currentMechanism ) {
                supportedMechanismsList = currentMechanism + SPACE + supportedMechanismsList;
            } else {
                if (i != supportedMechanisms.begin())
                    supportedMechanismsList += SPACE;
                supportedMechanismsList += currentMechanism;
            }
        }
    }

    if (serverProperties.isSet(QPID_FED_TAG)) {
        connection.setFederationPeerTag(serverProperties.getAsString(QPID_FED_TAG));
    }

    FieldTable ft = connection.getBroker().getLinkClientProperties();
    ft.setInt(QPID_FED_LINK,1);
    ft.setString(QPID_FED_TAG, connection.getBroker().getFederationTag());

    string response;
    if (sasl.get()) {
        const qpid::sys::SecuritySettings& ss = connection.getExternalSecuritySettings();
        if (sasl->start ( requestedMechanism.empty()
                          ? supportedMechanismsList
                          : requestedMechanism,
                          response,
                          & ss )) {
            proxy.startOk ( ft, sasl->getMechanism(), response, en_US );
        } else {
            //response was null
            ConnectionStartOkBody body;
            body.setClientProperties(ft);
            body.setMechanism(sasl->getMechanism());
            //Don't set response, as none was given
            body.setLocale(en_US);
            proxy.send(body);
        }
    }
    else {
        response = ((char)0) + username + ((char)0) + password;
        proxy.startOk ( ft, requestedMechanism, response, en_US );
    }

}

void ConnectionHandler::Handler::secure(const string& challenge )
{
    if (serverMode) {
        throw ConnectionForcedException("Invalid protocol sequence.");
    }

    if (sasl.get()) {
        string response = sasl->step(challenge);
        proxy.secureOk(response);
    }
    else {
        proxy.secureOk("");
    }
}

void ConnectionHandler::Handler::tune(uint16_t channelMax,
                                      uint16_t maxFrameSizeProposed,
                                      uint16_t /*heartbeatMin*/,
                                      uint16_t heartbeatMax)
{
    if (serverMode) {
        throw ConnectionForcedException("Invalid protocol sequence.");
    }

    maxFrameSize = std::min(maxFrameSize, maxFrameSizeProposed);
    connection.setFrameMax(maxFrameSize);

    // this method is only ever called when this Connection
    // is a federation link where this Broker is acting as
    // a client to another Broker
    sys::Duration interval = connection.getBroker().getLinkHeartbeatInterval();
    uint16_t intervalSec = static_cast<uint16_t>(interval/sys::TIME_SEC);
    uint16_t hb = std::min(intervalSec, heartbeatMax);
    connection.setHeartbeat(hb);
    connection.startLinkHeartbeatTimeoutTask();

    proxy.tuneOk(channelMax, maxFrameSize, hb);
    proxy.open("/", Array(), true);
}

void ConnectionHandler::Handler::openOk(const framing::Array& knownHosts)
{
    if (serverMode) {
        throw ConnectionForcedException("Invalid protocol sequence.");
    }

    for (Array::ValueVector::const_iterator i = knownHosts.begin(); i != knownHosts.end(); ++i) {
        Url url((*i)->get<std::string>());
        connection.getKnownHosts().push_back(url);
    }

    if (sasl.get()) {
        std::auto_ptr<qpid::sys::SecurityLayer> securityLayer = sasl->getSecurityLayer(maxFrameSize);

        if ( securityLayer.get() ) {
          secured->activateSecurityLayer(securityLayer, true);
        }

        saslUserId = sasl->getUserId();
    }

    isOpen = true;
}

void ConnectionHandler::Handler::redirect(const string& /*host*/, const framing::Array& /*knownHosts*/)
{

}
