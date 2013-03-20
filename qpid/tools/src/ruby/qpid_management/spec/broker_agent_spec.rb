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

require 'spec_helper'

describe Qpid::Management::BrokerAgent do
  before(:each) do
    @broker_port = `qpidd --no-data-dir --auth=no --no-module-dir --daemon --port 0`.chop
    @connection = Qpid::Messaging::Connection.new(url:"localhost:#{@broker_port}")
    @connection.open()
    @agent = Qpid::Management::BrokerAgent.new(@connection)
  end

  after(:each) do
    @agent.close()
    @connection.close()
    `qpidd -q --port #{@broker_port}`
  end

  describe '#broker' do
    let(:broker) { @agent.broker }

    it 'returns the broker' do
      broker.class.should == Qpid::Management::Broker
    end
  end
end
