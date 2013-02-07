#--
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
#++

module Qpid

  module Messaging

    # Establishes a connection to a remote endpoint.
    class Connection

      attr_reader :options # :nodoc:

      # Creates a connection object, but does not actually connect to
      # the specified location.
      #
      # ==== Options
      #
      #   :url - the URL for the broker (def. +"localhost"+)
      #   :options - connection options (def. +{}+)
      #
      # ==== Controlling Reconnect Behavior
      #
      # The following connection options can be used to configure
      # the reconnection behavior for this connection.
      #
      # * :username
      # * :password
      # * :heartbeat
      # * :tcp_nodelay
      # * :sasl_mechanism
      # * :sasl_service
      # * :sasl_min_ssf
      # * :sasl_max_ssf
      # * :transport
      # * :reconnect - +true+ or +false+; indicates wehtehr to attempt reconnections
      # * :reconnect_timeout - the number of seconds to attempt reconnecting
      # * :reconnect_limit - the number of retries before reporting failure
      # * :reconnect_interval_min - initial delay, in seconds, before attempting a reconnection
      # * :reconnect_interval_max - number of seconds to wait before additional reconnect attempts
      # * :reconnect_interval - shorthand for setting both min and max values
      # * :reconnect_urls - a list of alternate URLs to use for reconnection attempts
      #
      # ==== Examples
      #
      #   conn = Qpid::Messaging::Connnection.new
      #   conn = Qpid::Messaging::Connection.new :url => "amqp:tcp:broker1.domain.com:5672"
      #   conn = Qpid::Messaging::Connection.new :options => {:username => "login", :password => "password"}
      #
      def initialize(opts = {})
        @url = opts[:url] || "localhost"
        @options = convert_options(opts[:options] || {})
        @connection_impl = opts[:impl] || Cqpid::Connection.new(@url, @options)
      end

      def connection_impl # :nodoc:
        @connection_impl
      end

      # Establishes the connection.
      #
      # ==== Examples
      #
      #   conn.open unless conn.open?
      #
      def open
        @connection_impl.open
      end

      # Reports whether the connection is open.
      #
      # ==== Examples
      #
      #   conn.close if conn.open?
      #
      def open?; true && !@connection_impl.nil? && @connection_impl.isOpen; end

      # Closes the connection.
      def close; @connection_impl.close; end

      # Creates a new session.
      #
      # ==== Arguments
      #
      # * :name - specifies the name for this session
      # * :transactional - if +true+ then a creates a transaction session (def. +false+)
      #
      # ==== Examples
      #
      #   session = conn.create_session :name => "session1"
      #   session = conn.create_session :transaction => true
      #
      def create_session(args = {})
        name = args[:name] || ""
        if open?
          if args[:transactional]
            session = @connection_impl.createTransactionalSession name
          else
            session = @connection_impl.createSession name
          end
          return Session.new(self, session)
        else
          raise RuntimeError.new "No connection available."
        end
      end

      # Returns a Session with the given name.
      #
      # If no such Session exists then a MessagingException is raised.
      # == Options
      #
      # * +name+ - the session name
      #
      # == Examples
      #
      #   # retrieve a session named 'mysession' from the current connection
      #   name = "my-session"
      #   begin
      #     session = conn.session name
      #   rescue MessagingException => error
      #      puts "No such session: #{name}."
      #   end
      #
      def session name
        session_impl = @connection_impl.getSession name
        Qpid::Messaging::Session.new self, session_impl if session_impl
      end

      # Returns the username used to authenticate with the connection.
      def authenticated_username; @connection_impl.getAuthenticatedUsername if open?; end

      private

      def convert_options(options)
        result = {}
        unless options.nil? || options.empty?
          options.each_pair {|key, value| result[key.to_s] = value.to_s}
        end

        return result
      end

    end

  end

end

