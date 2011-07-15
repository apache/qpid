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

$:.unshift File.join(File.dirname(__FILE__), "..", "lib")

require 'test/unit'
require 'flexmock/test_unit'

require 'cqpid'
require 'qpid/encoding'

class TestEncoding < Test::Unit::TestCase

  def setup
    @cqpid = flexmock(Cqpid)

    @message = flexmock("message")
    @message_impl = flexmock("message_impl")

    @encoded = {"foo" => "bar"}
  end

  def test_encode_map_with_symbols
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @cqpid.
      should_receive(:encode).
      once.
      with({"foo" => "bar"}, @message_impl).
      and_return(@encoded)

    result = Qpid::Messaging.encode({:foo => :bar}, @message)

    assert_same @encoded, result
  end

  def test_encode_list_with_symbols
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @cqpid.
      should_receive(:encode).
      once.
      with(["foo", "bar"], @message_impl).
      and_return(@encoded)

    result = Qpid::Messaging.encode([:foo, :bar], @message)

    assert_same @encoded, result
  end

  def test_encode_with_content_type
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @cqpid.
      should_receive(:encode).
      once.
      with({"foo" => "bar"}, @message_impl).
      and_return(@encoded)

    result = Qpid::Messaging.encode({:foo => :bar}, @message)

    assert_same @encoded, result
  end

  def test_encode
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @cqpid.
      should_receive(:encode).
      once.
      with({"foo" => "bar"}, @message_impl).
      and_return(@encoded)

    result = Qpid::Messaging.encode({"foo" => "bar"}, @message)

    assert_same @encoded, result
  end

  def test_decode_for_map
    decoded = {"foo" => "bar"}
    @message.
      should_receive(:content_type).
      once.
      and_return("amqp/map")
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @cqpid.
      should_receive(:decodeMap).
      once.
      with(@message_impl).
      and_return(decoded)

    result = Qpid::Messaging.decode(@message)

    assert_same decoded, result
  end

  def test_decode_for_list
    decoded = ["foo", "bar"]
    @message.
      should_receive(:content_type).
      once.
      and_return("amqp/list")
    @message.
      should_receive(:message_impl).
      once.
      and_return(@message_impl)
    @cqpid.
      should_receive(:decodeList).
      once.
      with(@message_impl).
      and_return(decoded)

    result = Qpid::Messaging.decode(@message)

    assert_same decoded, result
  end

end

