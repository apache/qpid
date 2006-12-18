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

require "thread"
require "qpid/peer"
require "qpid/queue"

module Qpid

  class Client
    def initialize(host, port, spec, vhost = nil)
      @host = host
      @port = port
      @spec = spec
      @vhost = if vhost.nil?; host else vhost end

      @mechanism = nil
      @response = nil
      @locale = nil

      @queues = {}
      @mutex = Mutex.new()

      @closed = false
      @started = ConditionVariable.new()

      @conn = Connection.new(@host, @port, @spec)
      @peer = Peer.new(@conn, ClientDelegate.new(self))
    end

    attr_reader :mechanism, :response, :locale

    def closed?; @closed end

    def wait()
      @mutex.synchronize do
        @started.wait(@mutex)
      end
      raise EOFError.new() if closed?
    end

    def signal_start()
      @started.broadcast()
    end

    def queue(key)
      @mutex.synchronize do
        q = @queues[key]
        if q.nil?
          q = Queue.new()
          @queues[key] = q
        end
        return q
      end
    end

    def start(response, mechanism="AMQPLAIN", locale="en_US")
      @response = response
      @mechanism = mechanism
      @locale = locale

      @conn.connect()
      @conn.init()
      @peer.start()
      wait()
      channel(0).connection_open(@vhost)
    end

    def channel(id)
      return @peer.channel(id)
    end
  end

  class ClientDelegate
    include Delegate

    def initialize(client)
      @client = client
    end

    def connection_start(ch, msg)
      ch.connection_start_ok(:mechanism => @client.mechanism,
                             :response => @client.response,
                             :locale => @client.locale)
    end

    def connection_tune(ch, msg)
      ch.connection_tune_ok(*msg.fields)
      @client.signal_start()
    end
  end

end
