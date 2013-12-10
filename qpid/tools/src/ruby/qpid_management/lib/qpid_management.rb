#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

require 'qpid_management/broker_agent'
require 'qpid_management/broker_object'
require 'qpid_management/acl'
require 'qpid_management/binding'
require 'qpid_management/bridge'
require 'qpid_management/broker'
require 'qpid_management/cluster'
require 'qpid_management/connection'
require 'qpid_management/errors'
require 'qpid_management/exchange'
require 'qpid_management/ha_broker'
require 'qpid_management/link'
require 'qpid_management/memory'
require 'qpid_management/queue'
require 'qpid_management/session'
require 'qpid_management/subscription'

module Qpid
  # The Qpid Management framework is a management framework for Qpid brokers
  # that uses QMF2.
  #
  # ==== Example Usage
  #
  # Here is a simple example. It TODO.
  #
  #   require 'rubygems'
  #   require 'qpid_messaging'
  #   require 'qpid_management'
  #
  #   # create a connection and open it
  #   conn = Qpid::Messaging::Connection.new(:url => "broker.myqpiddomain.com")
  #   conn.open()
  #
  #   # create a broker agent
  #   agent = Qpid::Management::BrokerAgent.new(conn)
  #
  #   # get a reference to the broker
  #   broker = agent.broker
  #
  #   # print out all exchange names
  #   puts broker.exchanges.map(&:name)
  #
  #   # print out info about a single exchange
  #   amq_direct = broker.exchange('amq.direct')
  #   puts amq_direct
  #   puts amq_direct.msgDrops
  #
  #   # create an exchange
  #   broker.add_exchange('topic', 'myexchange')
  #
  #   # print out all queue names
  #   puts broker.queues.map(&:name)
  #
  #   # create a queue
  #   broker.add_queue('myqueue')
  #
  #   # print out info about a single queue
  #   myqueue = broker.queue('myqueue')
  #   puts myqueue.msgDepth
  module Management
  end
end
