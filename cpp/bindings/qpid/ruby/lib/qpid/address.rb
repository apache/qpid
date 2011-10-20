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

    # Address represents an address to which messages can be sent or from
    # which they can be received.
    #
    # An Address can be described using the following pattern:
    #
    # <address> [ / <subject> ] ; [ { <key> : <value> , ... } ]
    #
    # where *address* is a simple name and *subject* is a subject or subject
    # pattern.
    #
    # The options, enclosed in curly braces, are key:value pairs delimited by
    # a comma. The values can be nested maps also enclosed in curly braces.
    # Or they can be lists of values, where they are contained within square
    # brackets but still comma delimited, such as:
    #
    # [value1,value2,value3]
    #
    # The following are the list of supported options:
    #
    # create:: Indicates if the address should be created; values are *always*,
    #          *never*, *sender* or *reciever*.
    #
    # assert:: Indicates whether or not to assert any specified node properties;
    #          values are *always*, *never*, *sender* or *receiver*.
    #
    # delete:: Indicates whether or not to delete the addressed node when a
    #          sender or receiver is cancelled; values are *always*, *never*,
    #          *sender* or *receiver*.
    #
    # node:: A nested map describing properties for the addressed node.
    #        Properties are *type* (*topic* or *queue*), *durable* (a boolean),
    #        *x-declare* (a nested map of amqp 0.10-specific options) and
    #        *x-bindings*. (nested list which specifies a queue, exchange or
    #        a binding key and arguments.
    #
    # link:: A nested map through which properties of the link can be specified;
    #        properties are *durable*, *reliability*, *x-declare*, *x-subscribe*
    #        and *x-bindings*.
    #
    # mode:: (*For receivers only*) indicates whether the receiver should consume
    #        or browse messages; values are *consume* (the default) and *browse*.
    class Address

      def initialize(name, subject, options = {}, _type = "", address_impl = nil)
        @address_impl = address_impl || Cqpid::Address.new(name, subject, convert_options(options), _type)
      end

       def address_impl # :nodoc:
         @address_impl
       end

       # Returns the name.
      def name; @address_impl.getName; end

      # Sets the name.
      def name=(name); @address_impl.setName name; end

      # Returns the subject.
      def subject; @address_impl.getSubject; end

      # Sets the subject.
      def subject=(subject); @address_impl.setSubject(subject); end

      # Returns the type.
      #---
      # We cannot use "type" since that clashes with the Ruby object.type
      # identifier.
      def _type; @address_impl.getType; end

      # Sets the type.
      #
      # The type of the address determines how Sender and Receiver objects
      # are constructed for it. If no type is specified then it will be
      # determined by querying the broker.
      def _type=(_type); @address_impl.setType(_type); end

      # Returns the options.
      def options; @address_impl.getOptions; end

      # Sets the options for the address.
      # Any symbols are converted to strings.
      def options=(options); @address_impl.setOptions(convert_options(options)); end

      def to_s; @address_impl.str; end

      private

      def convert_options(options)
        result = {}
        options.each_pair {|key, value| result[key.to_s] = value.to_s}

        return result
      end

    end

  end

end

