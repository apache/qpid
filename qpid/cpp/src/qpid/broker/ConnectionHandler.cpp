
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

#include "ConnectionHandler.h"
#include "Connection.h"
#include "SecureConnection.h"
#include "qpid/Url.h"
#include "qpid/framing/ClientInvoker.h"
#include "qpid/framing/ServerInvoker.h"
#include "qpid/framing/enum.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/SecurityLayer.h"
#include "AclModule.h"
#include "qmf/org/apache/qpid/broker/EventClientConnectFail.h"

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;
using qpid::sys::SecurityLayer;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace
{
const std::string ANONYMOUS = "ANONYMOUS";
const std::string PLAIN     = "PLAIN";
const std::string en_US     = "en_US";
const std::string QPID_FED_LINK = "qpid.fed_link";
const std::string QPID_FED_TAG  = "qpid.federation_tag";
const std::string SESSION_FLOW_CONTROL("qpid.session_flow");
const int SESSION_FLOW_CONTROL_VER = 1;
}

void ConnectionHandler::close(connection::CloseCode code, const string& text)
{
    handler->client.close(code, text);
}

void ConnectionHandler::heartbeat()
{
    handler->client.heartbeat();
}

void ConnectionHandler::handle(framing::AMQFrame& frame)
{
    AMQMethodBody* method=frame.getBody()->getMethod();
    try{
        bool handled = false;
        if (handler->serverMode) {
            handled = invoke(static_cast<AMQP_ServerOperations::ConnectionHandler&>(*handler.get()), *method);
        } else {
            handled = invoke(static_cast<AMQP_ClientOperations::ConnectionHandler&>(*handler.get()), *method);
        }
        if (!handled) {
            handler->connection.getChannel(frame.getChannel()).in(frame);
        }

    }catch(ConnectionException& e){
        handler->client.close(e.code, e.what());
    }catch(std::exception& e){
        handler->client.close(541/*internal error*/, e.what());
    }
}

void ConnectionHandler::setSecureConnection(SecureConnection* secured)
{
    handler->secured = secured;
}

ConnectionHandler::ConnectionHandler(Connection& connection, bool isClient)  : handler(new Handler(connection, isClient)) {}

ConnectionHandler::Handler::Handler(Connection& c, bool isClient) :
    client(c.getOutput()), server(c.getOutput()),
    connection(c), serverMode(!isClient), acl(0), secured(0)
{
    if (serverMode) {

    	acl =  connection.getBroker().getAcl();

        FieldTable properties;
        Array mechanisms(0x95);

        properties.setString(QPID_FED_TAG, connection.getBroker().getFederationTag());

        authenticator = SaslAuthenticator::createAuthenticator(c);
        authenticator->getMechanisms(mechanisms);

        Array locales(0x95);
        boost::shared_ptr<FieldValue> l(new Str16Value(en_US));
        locales.add(l);
        client.start(properties, mechanisms, locales);
    }
}


ConnectionHandler::Handler::~Handler() {}


void ConnectionHandler::Handler::startOk(const framing::FieldTable& clientProperties,
                                         const string& mechanism,
                                         const string& response,
                                         const string& /*locale*/)
{
    try {
        authenticator->start(mechanism, response);
    } catch (std::exception& /*e*/) {
        management::ManagementAgent* agent = connection.getAgent();
        if (agent) {
            string error;
            string uid;
            authenticator->getError(error);
            authenticator->getUid(uid);
            agent->raiseEvent(_qmf::EventClientConnectFail(connection.getMgmtId(), uid, error));
        }
        throw;
    }
    connection.setFederationLink(clientProperties.get(QPID_FED_LINK));
    connection.setFederationPeerTag(clientProperties.getAsString(QPID_FED_TAG));
    if (connection.isFederationLink()) {
    	if (acl && !acl->authorise(connection.getUserId(),acl::ACT_CREATE,acl::OBJ_LINK,"")){
            client.close(framing::connection::CLOSE_CODE_CONNECTION_FORCED,"ACL denied creating a federation link");
            return;
        }
        QPID_LOG(info, "Connection is a federation link");
    }
    if ( clientProperties.getAsInt(SESSION_FLOW_CONTROL) == SESSION_FLOW_CONTROL_VER ) {
        connection.setClientThrottling();
    }
}

void ConnectionHandler::Handler::secureOk(const string& response)
{
    try {
        authenticator->step(response);
    } catch (std::exception& /*e*/) {
        management::ManagementAgent* agent = connection.getAgent();
        if (agent) {
            string error;
            string uid;
            authenticator->getError(error);
            authenticator->getUid(uid);
            agent->raiseEvent(_qmf::EventClientConnectFail(connection.getMgmtId(), uid, error));
        }
        throw;
    }
}

void ConnectionHandler::Handler::tuneOk(uint16_t /*channelmax*/,
    uint16_t framemax, uint16_t heartbeat)
{
    connection.setFrameMax(framemax);
    connection.setHeartbeatInterval(heartbeat);
}

void ConnectionHandler::Handler::open(const string& /*virtualHost*/,
                                      const framing::Array& /*capabilities*/, bool /*insist*/)
{
    std::vector<Url> urls = connection.broker.getKnownBrokers();
    framing::Array array(0x95); // str16 array
    for (std::vector<Url>::iterator i = urls.begin(); i < urls.end(); ++i) 
        array.add(boost::shared_ptr<Str16Value>(new Str16Value(i->str())));
    client.openOk(array);

    //install security layer if one has been negotiated:
    if (secured) {
        std::auto_ptr<SecurityLayer> sl = authenticator->getSecurityLayer(connection.getFrameMax());
        if (sl.get()) secured->activateSecurityLayer(sl);
    }
}

        
void ConnectionHandler::Handler::close(uint16_t replyCode, const string& replyText)
{
    if (replyCode != 200) {
        QPID_LOG(warning, "Client closed connection with " << replyCode << ": " << replyText);
    }

    if (replyCode == framing::connection::CLOSE_CODE_CONNECTION_FORCED)
        connection.notifyConnectionForced(replyText);

    client.closeOk();
    connection.getOutput().close();
} 
        
void ConnectionHandler::Handler::closeOk(){
    connection.getOutput().close();
} 

void ConnectionHandler::Handler::heartbeat(){
	// Do nothing - the purpose of heartbeats is just to make sure that there is some
	// traffic on the connection within the heart beat interval, we check for the
	// traffic and don't need to do anything in response to heartbeats
}

void ConnectionHandler::Handler::start(const FieldTable& serverProperties,
                                       const framing::Array& /*mechanisms*/,
                                       const framing::Array& /*locales*/)
{
    string mechanism = connection.getAuthMechanism();
    string response  = connection.getAuthCredentials();

    connection.setFederationPeerTag(serverProperties.getAsString(QPID_FED_TAG));

    FieldTable ft;
    ft.setInt(QPID_FED_LINK,1);
    ft.setString(QPID_FED_TAG, connection.getBroker().getFederationTag());
    server.startOk(ft, mechanism, response, en_US);
}

void ConnectionHandler::Handler::secure(const string& /*challenge*/)
{
    server.secureOk("");
}

void ConnectionHandler::Handler::tune(uint16_t channelMax,
                                      uint16_t frameMax,
                                      uint16_t /*heartbeatMin*/,
                                      uint16_t heartbeatMax)
{
    connection.setFrameMax(frameMax);
    connection.setHeartbeat(heartbeatMax);
    server.tuneOk(channelMax, frameMax, heartbeatMax);
    server.open("/", Array(), true);
}

void ConnectionHandler::Handler::openOk(const framing::Array& knownHosts)
{
    for (Array::ValueVector::const_iterator i = knownHosts.begin(); i != knownHosts.end(); ++i) {
        Url url((*i)->get<std::string>());
        connection.getKnownHosts().push_back(url);
    }
}

void ConnectionHandler::Handler::redirect(const string& /*host*/, const framing::Array& /*knownHosts*/)
{

}
