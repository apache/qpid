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

describe Qpid::Management::Broker do
  before(:each) do
    @broker_port = `qpidd --no-data-dir --auth=no --no-module-dir --daemon --port 0`.chop
    @connection = Qpid::Messaging::Connection.new(url:"localhost:#{@broker_port}")
    @connection.open()
    @agent = Qpid::Management::BrokerAgent.new(@connection)
    @broker = @agent.broker
  end

  after(:each) do
    @agent.close()
    @connection.close()
    `qpidd --quit --port #{@broker_port}`
  end

  def setup_queue_route
    @other_port = `qpidd --no-data-dir --auth=no --no-module-dir --daemon --port 0`.chop
    @broker.add_link('link1', 'localhost', @other_port)
    @broker.add_queue('queue')
    @broker.add_queue_route('qr1',
                            link: 'link1',
                            queue: 'queue',
                            exchange: 'amq.direct',
                            sync: 2)
  end

  %w(connection session subscription exchange queue binding link bridge).each do |type|
    describe "##{type}s" do
      before(:each) do
        setup_queue_route if %w(link bridge).include?(type)
      end

      after(:each) do
        if %w(link bridge).include?(type)
          `qpidd --quit --port #{@other_port}`
        end
      end

      let(:collection) { @broker.send("#{type}s") }

      it "returns at least 1 #{type}" do
        if type == 'subscription'
          session = @connection.create_session
          receiver = session.create_receiver("amq.direct/temp")
        end
        collection.count.should be > 0
      end
    end

    describe "##{type}" do
      before(:each) do
        setup_queue_route if %w(link bridge).include?(type)
      end

      after(:each) do
        if %w(link bridge).include?(type)
          `qpidd --quit --port #{@other_port}`
        end
      end

      let(:object) { @broker.send("#{type}s")[0] }

      it "returns a #{type} by oid" do
        if type == 'subscription'
          session = @connection.create_session
          receiver = session.create_receiver("amq.direct/temp")
        end
        @broker.send(type, object.short_id).id.should == object.id
      end
    end
  end

  describe '#add_exchange' do
    %w(fanout direct topic headers).each do |type|
      context "when adding a #{type} exchange" do
        let(:exchange_name) { "#{type}1" }
        before(:each) do
          @before_creation = Time.now
          @broker.add_exchange(type, exchange_name, {'qpid.replicate' => 'none'})
        end

        subject { @broker.exchange(exchange_name) }
        its(:short_id) { should == exchange_name }
        its(:type) { should == type }
        its(:created_at) { should be > @before_creation }
        it 'has the correct arguments' do
          subject.arguments.should == {'qpid.replicate' => 'none'}
        end
      end
    end
  end

  describe "#delete_exchange" do
    before(:each) do
      @before_creation = Time.now
      @broker.add_exchange('fanout', 'fanout_to_delete')
    end

    let(:exchange) { @broker.exchange('fanout_to_delete') }

    context "with a valid exchange name" do
      it "deletes the exchange" do
        @broker.delete_exchange(exchange.short_id)
        expect { exchange.refresh! }.to raise_error
      end
    end

    context "with an invalid exchange name" do
      it "raises a not-found exception" do
        expect { @broker.delete_exchange("badname") }.to raise_error(/not-found.*badname/)
      end
    end
  end

  describe "#add_queue" do
    before(:each) do
      @before_creation = Time.now
      @queue_name = 'myqueue'
      @broker.add_queue(@queue_name, {'qpid.replicate' => 'none'})
    end

    subject { @broker.queue(@queue_name) }
    its(:short_id) { should == @queue_name }
    its(:created_at) { should be > @before_creation }
    it 'has the correct arguments' do
      subject.arguments.should == {'qpid.replicate' => 'none'}
    end
  end

  describe "#delete_queue" do
    before(:each) do
      @before_creation = Time.now
      @broker.add_queue('queue_to_delete')
    end

    let(:queue) { @broker.queue('queue_to_delete') }

    context "with a valid queue name" do
      it "deletes the queue" do
        @broker.delete_queue(queue.short_id)
        expect { queue.refresh! }.to raise_error
      end
    end

    context "with an invalid name" do
      it "raises a not-found exception" do
        expect { @broker.delete_queue("badname") }.to raise_error(/not-found.*badname/)
      end
    end
  end

  describe "#add_binding" do
    before(:each) do
      @broker.add_queue('queue')
    end

    it "creates a binding for a fanout exchange" do
      @broker.add_exchange('fanout', 'fanout')
      @broker.add_binding('fanout', 'queue')
      expect { @broker.binding('org.apache.qpid.broker:exchange:fanout,org.apache.qpid.broker:queue:queue,') }.to_not raise_error
    end

    it "creates a binding for a direct exchange" do
      @broker.add_exchange('direct', 'direct')
      @broker.add_binding('direct', 'queue', 'mykey')
      expect { @broker.binding('org.apache.qpid.broker:exchange:direct,org.apache.qpid.broker:queue:queue,mykey') }.to_not raise_error
    end

    it "creates a binding for a topic exchange" do
      @broker.add_exchange('topic', 'topic')
      @broker.add_binding('topic', 'queue', 'us.#')
      expect { @broker.binding('org.apache.qpid.broker:exchange:topic,org.apache.qpid.broker:queue:queue,us.#') }.to_not raise_error
    end
  end

  describe "#delete_binding" do
    it "deletes an existing binding" do
      @broker.add_queue('queue')
      @broker.add_exchange('fanout', 'fanout')
      @broker.add_binding('fanout', 'queue')
      expect { @broker.delete_binding('fanout', 'queue') }.to_not raise_error
    end
  end

  describe "#add_link" do
    before(:each) do
      @other_port = `/usr/sbin/qpidd --no-data-dir --auth=no --no-module-dir --daemon --port 0`.chop
    end

    after(:each) do
      `/usr/sbin/qpidd -q --port #{@other_port}`
    end

    it "adds a link" do
      @broker.add_link('link1', 'localhost', @other_port)
      @broker.links.count.should == 1
    end
  end

  describe "#delete_link" do
    before(:each) do
      @other_port = `/usr/sbin/qpidd --no-data-dir --auth=no --no-module-dir --daemon --port 0`.chop
      @broker.add_link('link1', 'localhost', @other_port)
    end

    after(:each) do
      `/usr/sbin/qpidd -q --port #{@other_port}`
    end

    it "deletes a link" do
      @broker.delete_link('link1')
      @broker.links.count.should == 0
    end
  end

  describe "#add_queue_route" do
    context "with missing options" do
      [:link, :queue, :exchange, :sync].each do |opt|
        opts = {link: 'l', queue: 'q', exchange: 'e', sync:2}
        opts.delete(opt)
        it "raises an error when :#{opt} is missing" do
          expect { @broker.add_queue_route('name', opts) }.to raise_error(/Option :#{opt} is required/)
        end
      end
    end

    context "with all required options" do
      before(:each) do
        @other_port = `/usr/sbin/qpidd --no-data-dir --auth=no --no-module-dir --daemon --port 0`.chop
        @broker.add_link('link1', 'localhost', @other_port)
        @broker.add_queue('queue')
        @broker.add_queue_route('qr1',
                                link: 'link1',
                                queue: 'queue',
                                exchange: 'amq.direct',
                                sync: 2)
      end

      after(:each) do
        `/usr/sbin/qpidd -q --port #{@other_port}`
      end

      it "adds a queue route" do
        @broker.bridges.count.should == 1
      end

      subject { @broker.bridges[0] }
      its(:dest) { should == 'amq.direct' }
      its(:durable) { should == false }
      its(:dynamic) { should == false }
      its(:excludes) { should == "" }
      its(:key) { should == "" }
      its(:name) { should == "qr1" }
      its(:src) { should == "queue" }
      its(:srcIsLocal) { should == false }
      its(:srcIsQueue) { should == true }
      its(:sync) { should == 2 }
      its(:tag) { should == "" }
    end
  end

  describe "#add_exchange_route" do
    context "with missing options" do
      [:link, :exchange, :key, :sync].each do |opt|
        opts = {link: 'l', exchange: 'e', key:'rk', sync:2}
        opts.delete(opt)
        it "raises an error when :#{opt} is missing" do
          expect { @broker.add_exchange_route('name', opts) }.to raise_error(/Option :#{opt} is required/)
        end
      end
    end

    context "with all required options" do
      before(:each) do
        @other_port = `/usr/sbin/qpidd --no-data-dir --auth=no --no-module-dir --daemon --port 0`.chop
        @broker.add_link('link1', 'localhost', @other_port)
        @broker.add_queue('queue')
        @broker.add_exchange_route('er1',
                                link: 'link1',
                                exchange: 'amq.direct',
                                key: 'foo',
                                sync: 2)
      end

      after(:each) do
        `/usr/sbin/qpidd -q --port #{@other_port}`
      end

      it "adds an exchange route" do
        @broker.bridges.count.should == 1
      end

      subject { @broker.bridges[0] }
      its(:dest) { should == 'amq.direct' }
      its(:durable) { should == false }
      its(:dynamic) { should == false }
      its(:excludes) { should == "" }
      its(:key) { should == "foo" }
      its(:name) { should == "er1" }
      its(:src) { should == "amq.direct" }
      its(:srcIsLocal) { should == false }
      its(:srcIsQueue) { should == false }
      its(:sync) { should == 2 }
      its(:tag) { should == "" }
    end
  end

  describe "#add_dynamic_route" do
    context "with missing options" do
      [:link, :exchange, :sync].each do |opt|
        opts = {link: 'l', exchange: 'e', sync:2}
        opts.delete(opt)
        it "raises an error when :#{opt} is missing" do
          expect { @broker.add_dynamic_route('name', opts) }.to raise_error(/Option :#{opt} is required/)
        end
      end
    end

    context "with all required options" do
      before(:each) do
        @other_port = `/usr/sbin/qpidd --no-data-dir --auth=no --no-module-dir --daemon --port 0`.chop
        @broker.add_link('link1', 'localhost', @other_port)
        @broker.add_queue('queue')
        @broker.add_dynamic_route('dr1',
                                link: 'link1',
                                exchange: 'amq.direct',
                                sync: 2)
      end

      after(:each) do
        `/usr/sbin/qpidd -q --port #{@other_port}`
      end

      it "adds an exchange route" do
        @broker.bridges.count.should == 1
      end

      subject { @broker.bridges[0] }
      its(:dest) { should == 'amq.direct' }
      its(:durable) { should == false }
      its(:dynamic) { should == true }
      its(:excludes) { should == "" }
      its(:key) { should == "" }
      its(:name) { should == "dr1" }
      its(:src) { should == "amq.direct" }
      its(:srcIsLocal) { should == false }
      its(:srcIsQueue) { should == false }
      its(:sync) { should == 2 }
      its(:tag) { should == "" }
    end
  end
end
