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
#include "qpid/messaging/ConnectionOptions.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/types/Variant.h"
#include "qpid/log/Statement.h"
#include <algorithm>
#include <limits>

namespace qpid {
namespace messaging {

namespace {
double FOREVER(std::numeric_limits<double>::max());

double timeValue(const qpid::types::Variant& value) {
    if (types::isIntegerType(value.getType()))
        return double(value.asInt64());
    return value.asDouble();
}

void merge(const std::string& value, std::vector<std::string>& list) {
    if (std::find(list.begin(), list.end(), value) == list.end())
        list.push_back(value);
}

void merge(const qpid::types::Variant::List& from, std::vector<std::string>& to)
{
    for (qpid::types::Variant::List::const_iterator i = from.begin(); i != from.end(); ++i)
        merge(i->asString(), to);
}

}

ConnectionOptions::ConnectionOptions(const std::map<std::string, qpid::types::Variant>& options)
    : replaceUrls(false), reconnect(false), timeout(FOREVER), limit(-1), minReconnectInterval(0.001), maxReconnectInterval(2),
      retries(0), reconnectOnLimitExceeded(true), nestAnnotations(false), setToOnSend(false)
{
    for (qpid::types::Variant::Map::const_iterator i = options.begin(); i != options.end(); ++i) {
        set(i->first, i->second);
    }
}

void ConnectionOptions::set(const std::string& name, const qpid::types::Variant& value)
{
    if (name == "reconnect") {
        reconnect = value;
    } else if (name == "reconnect-timeout" || name == "reconnect_timeout") {
        timeout = timeValue(value);
    } else if (name == "reconnect-limit" || name == "reconnect_limit") {
        limit = value;
    } else if (name == "reconnect-interval" || name == "reconnect_interval") {
        maxReconnectInterval = minReconnectInterval = timeValue(value);
    } else if (name == "reconnect-interval-min" || name == "reconnect_interval_min") {
        minReconnectInterval = timeValue(value);
    } else if (name == "reconnect-interval-max" || name == "reconnect_interval_max") {
        maxReconnectInterval = timeValue(value);
    } else if (name == "reconnect-urls-replace" || name == "reconnect_urls_replace") {
        replaceUrls = value.asBool();
    } else if (name == "reconnect-urls" || name == "reconnect_urls") {
        if (replaceUrls) urls.clear();
        if (value.getType() == qpid::types::VAR_LIST) {
            merge(value.asList(), urls);
        } else {
            merge(value.asString(), urls);
        }
    } else if (name == "username") {
        username = value.asString();
    } else if (name == "password") {
        password = value.asString();
    } else if (name == "sasl-mechanism" || name == "sasl_mechanism" ||
               name == "sasl-mechanisms" || name == "sasl_mechanisms") {
        mechanism = value.asString();
    } else if (name == "sasl-service" || name == "sasl_service") {
        service = value.asString();
    } else if (name == "sasl-min-ssf" || name == "sasl_min_ssf") {
        minSsf = value;
    } else if (name == "sasl-max-ssf" || name == "sasl_max_ssf") {
        maxSsf = value;
    } else if (name == "heartbeat") {
        heartbeat = value;
    } else if (name == "tcp-nodelay" || name == "tcp_nodelay") {
        tcpNoDelay = value;
    } else if (name == "locale") {
        locale = value.asString();
    } else if (name == "max-channels" || name == "max_channels") {
        maxChannels = value;
    } else if (name == "max-frame-size" || name == "max_frame_size") {
        maxFrameSize = value;
    } else if (name == "bounds") {
        bounds = value;
    } else if (name == "transport") {
        protocol = value.asString();
    } else if (name == "ssl-cert-name" || name == "ssl_cert_name") {
        sslCertName = value.asString();
    } else if (name == "x-reconnect-on-limit-exceeded" || name == "x_reconnect_on_limit_exceeded") {
        reconnectOnLimitExceeded = value;
    } else if (name == "container-id" || name == "container_id") {
        identifier = value.asString();
    } else if (name == "nest-annotations" || name == "nest_annotations") {
        nestAnnotations = value;
    } else if (name == "set-to-on-send" || name == "set_to_on_send") {
        setToOnSend = value;
    } else if (name == "properties" || name == "client-properties" || name == "client_properties") {
        properties = value.asMap();
    } else {
        throw qpid::messaging::MessagingException(QPID_MSG("Invalid option: " << name << " not recognised"));
    }
}

}} // namespace qpid::messaging
