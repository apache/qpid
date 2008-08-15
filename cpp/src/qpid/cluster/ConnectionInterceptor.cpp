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
#include "ConnectionInterceptor.h"
#include "qpid/framing/ClusterConnectionCloseBody.h"
#include "qpid/framing/ClusterConnectionDoOutputBody.h"
#include "qpid/framing/AMQFrame.h"

namespace qpid {
namespace cluster {

using namespace framing;

template <class T, class U, class V> void shift(T& a, U& b, const V& c) { a = b; b = c; }

ConnectionInterceptor::ConnectionInterceptor(
    broker::Connection& conn, Cluster& clust, Cluster::ShadowConnectionId shadowId_)
    : connection(&conn), cluster(clust), isClosed(false), shadowId(shadowId_)
{
    connection->addFinalizer(boost::bind(operator delete, this));
    // Attach  my functions to Connection extension points.
    shift(receivedNext, connection->receivedFn, boost::bind(&ConnectionInterceptor::received, this, _1));
    shift(closedNext, connection->closedFn, boost::bind(&ConnectionInterceptor::closed, this));
    shift(doOutputNext, connection->doOutputFn, boost::bind(&ConnectionInterceptor::doOutput, this));
}

ConnectionInterceptor::~ConnectionInterceptor() {
    assert(connection == 0);
}

void ConnectionInterceptor::received(framing::AMQFrame& f) {
    if (isClosed) return;
    cluster.send(f, this);
}

void ConnectionInterceptor::deliver(framing::AMQFrame& f) {
    receivedNext(f);
}

void ConnectionInterceptor::closed() {
    if (isClosed) return;
    try {
        // Called when the local network connection is closed. We still
        // need to process any outstanding cluster frames for this
        // connection to ensure our sessions are up-to-date. We defer
        // closing the Connection object till deliverClosed(), but replace
        // its output handler with a null handler since the network output
        // handler will be deleted.
        // 
        connection->setOutputHandler(&discardHandler); 
        cluster.send(AMQFrame(in_place<ClusterConnectionCloseBody>()), this);
        isClosed = true;
    }
    catch (const std::exception& e) {
        QPID_LOG(error, QPID_MSG("While closing connection: " << e.what()));
    }
}

void ConnectionInterceptor::deliverClosed() {
    closedNext();
    // Drop reference so connection will be deleted, which in turn
    // will delete this via finalizer added in ctor.
    connection = 0;             
}

void ConnectionInterceptor::dirtyClose() {
    // Not closed via cluster self-delivery but closed locally.
    // Used for dirty cluster shutdown where active connections
    // must be cleaned up.
    connection = 0;
}

bool  ConnectionInterceptor::doOutput() {
    // FIXME aconway 2008-08-15: this is not correct.
    // Run in write threads so order of execution of doOutput is not determinate.
    // Will only work reliably for in single-consumer tests.   

    if (connection->hasOutput()) {
        cluster.send(AMQFrame(in_place<ClusterConnectionDoOutputBody>()), this);
        return doOutputNext();
    }
    return false;
}

void ConnectionInterceptor::deliverDoOutput() {
    // FIXME aconway 2008-08-15: see comment in doOutput.
    if (isShadow()) 
        doOutputNext();
}

}} // namespace qpid::cluster
