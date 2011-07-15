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

require 'cqpid'

module Qpid

  module Messaging

    # Connection allows for establishing connections to a remote endpoint.
    class Connection

      # The following general options are supported (as strings or symbols):
      #
      # username::
      # password::
      # heartbeat::
      # tcp_nodelay::
      # sasl_mechanism::
      # sasl_service::
      # sasl_min_ssf::
      # sasl_max_ssf::
      # transport::
      #
      # The following options specifically control reconnection behavior:
      #
      # reconnect:: *true* or *false*; indicates whether to attempt reconnections
      # reconnect_timeout:: the number of seconds to attempt reconnecting
      # reconnect_limit:: the number of retries before reporting failure
      # reconnect_interval_min:: initial delay, in seconds, before attempting a reconnecting
      # reconnect_interval_max:: number of seconds to wait before additional reconnect attempts
      # reconnect_interval:: shorthand for setting box min and max values
      # reconnect_urls:: a list of alternate URLs to use for reconnection attempts
      def initialize(url, options = {}, connection_impl = nil)
        @url = url
        @connection_impl = connection_impl
        @options = options
      end

      def connection_impl # :nodoc:
        @connection_impl
      end

      # Opens the connection.
      def open
        @connection_impl = Cqpid::Connection.new(@url, convert_options)
        @connection_impl.open
      end

      # Reports whether the connection is open.
      def open?; false || (@connection_impl.isOpen if @connection_impl); end

      # Closes the connection.
      def close; @connection_impl.close if open?; end

      # Creates a new session.
      #
      # If :transactional => true then a transactional session is created.
      # Otherwise a standard session is created.
      def create_session(args = {})
        name = args[:name] || ""
        if open?
          if args[:transactional]
            session = @connection_impl.createTransactionalSession name
          else
            session = @connection_impl.createSession name
          end
          return Session.new(session)
        else
          raise RuntimeError.new "No connection available."
        end
      end

      # Returns a session for the specified session name.
      def session name
        session_impl = @connection_impl.getSession name
        Qpid::Messaging::Session.new session_impl if session_impl
      end

      # Returns the username used to authenticate with the connection.
      def authenticated_username; @connection_impl.getAuthenticatedUsername if open?; end

      # inherited from Handle

      # Returns whether the underlying handle is valid; i.e., not null.
      def valid?
        @connection_impl.isValid
      end

      # Returns whether the underlying handle is null.
      def null?
        @connection_impl.isNull
      end

      # Swaps the underlying connection handle.
      def swap connection
        @connection_impl.swap connection.connection_impl
      end

      private

      def convert_options
        result = {}
        # map only those options defined in the C++ layer
        # TODO when new options are added, this needs to be updated.
        unless @options.nil? || @options.empty?
          @options.each_pair {|key, value| result[key.to_s] = value.to_s}
        end

        return result
      end

    end

  end

end

