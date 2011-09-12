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

require 'qpid'

class TestMessage < Test::Unit::TestCase

  def setup
    @address = flexmock("address")
    @address_impl = flexmock("address_impl")

    @messaging = flexmock(Qpid::Messaging)
    @message_impl = flexmock("message")
    @message = Qpid::Messaging::Message.new({}, @message_impl)
 end

  def test_message_impl
    assert_same @message_impl, @message.message_impl
  end

  def test_set_reply_to
    @address.
      should_receive(:address_impl).
      once.
      and_return(@address_impl)
    @message_impl.
      should_receive(:setReplyTo).
      once.
      with(@address_impl)

    @message.reply_to = @address
  end

  def test_get_reply_to
    @message_impl.
      should_receive(:getReplyTo).
      once.
      and_return(@address_impl)

    result = @message.reply_to

    assert_not_nil result
    assert_same @address_impl, result.address_impl
  end

  def test_set_subject
    @message_impl.
      should_receive(:setSubject).
      once.
      with("New Subject")

    @message.subject = "New Subject"
  end

  def test_get_subject
    @message_impl.
      should_receive(:getSubject).
      once.
      and_return("Old Subject")

    assert_equal "Old Subject", @message.subject
  end

  def test_set_content_type
    @message_impl.
      should_receive(:setContentType).
      once.
      and_return("amqp/map")

    @message.content_type = "amqp/map"
  end

  def test_get_content_type
    @message_impl.
      should_receive(:getContentType).
      once.
      and_return("amqp/list")

    assert_equal "amqp/list", @message.content_type
  end

  def test_set_message_id
    @message_impl.
      should_receive(:setMessageId).
      once.
      with("717")

    @message.message_id = "717"
  end

  def test_get_message_id
    @message_impl.
      should_receive(:getMessageId).
      once.
      and_return("1965")

    assert_equal "1965", @message.message_id
  end

  def test_set_user_id
    @message_impl.
      should_receive(:setUserId).
      once.
      with("129")

    @message.user_id = "129"
  end

  def test_get_user_id
    @message_impl.
      should_receive(:getUserId).
      once.
      and_return("1971")

    assert_equal "1971", @message.user_id
  end

  def test_set_correlation_id
    @message_impl.
      should_receive(:setCorrelationId).
      once.
      with("320")

    @message.correlation_id = "320"
  end

  def test_get_correlation_id
    @message_impl.
      should_receive(:getCorrelationId).
      once.
      and_return("1996")

    assert_equal "1996", @message.correlation_id
  end

  def test_set_priority
    @message_impl.
      should_receive(:setPriority).
      once.
      with(9)

    @message.priority = 9
  end

  def test_get_priority
    @message_impl.
      should_receive(:getPriority).
      once.
      and_return(21)

    assert_equal 21, @message.priority
  end

  def test_set_ttl
    @message_impl.
      should_receive(:setTtl).
      once.
      with(Qpid::Messaging::Duration::FOREVER)

    @message.ttl = Qpid::Messaging::Duration::FOREVER
  end

  def test_get_ttl
    @message_impl.
      should_receive(:getTtl).
      once.
      and_return(Qpid::Messaging::Duration::SECOND)

    assert_equal Qpid::Messaging::Duration::SECOND, @message.ttl
  end

  def test_set_durable
    @message_impl.
      should_receive(:setDurable).
      once.
      with(true)

    @message.durable = true
  end

  def test_set_not_durable
    @message_impl.
      should_receive(:setDurable).
      once.
      with(false)

    @message.durable = false
  end

  def test_get_durable
    @message_impl.
      should_receive(:getDurable).
      once.
      and_return(true)

    assert @message.durable
  end

  def test_set_redelivered
    @message_impl.
      should_receive(:setRedelivered).
      once.
      with(true)

    @message.redelivered = true
  end

  def test_set_not_redelivered
    @message_impl.
      should_receive(:setRedelivered).
      once.
      with(false)

    @message.redelivered = false
  end

  def test_get_redelivered
    @message_impl.
      should_receive(:getRedelivered).
      once.
      and_return(false)

    assert !@message.redelivered
  end

  def test_get_properties
    properties = {"foo" => "bar"}
    @message_impl.
      should_receive(:getProperties).
      once.
      and_return(properties)

    result = @message.properties

    assert_equal properties, result
  end

  def test_get_property
    @message_impl.
      should_receive(:getProperties).
      once.
      and_return({"foo" => "bar"})

    result = @message["foo"]

    assert_equal "bar", result
  end

  def test_set_property
    @message_impl.
      should_receive(:setProperty).
      once.
      with("foo", "bar")

    @message["foo"] = "bar"
  end

  def test_set_content
    @message_impl.
      should_receive(:setContent).
      once.
      with("foo")

    @message.content = "foo"
    assert_equal "foo", @message.content
  end

  def test_set_content_with_array
    content = ["one", "two", "three"]

    @messaging.
      should_receive(:encode).
      once.
      with(content, @message, "amqp/list")

    @message.content = content
    assert_same content, @message.content
  end

  def test_set_content_with_map
    content = {:foo => "bar", :dog => "cat"}

    @messaging.
      should_receive(:encode).
      once.
      with(content, @message, "amqp/map")

    @message.content = content
    assert_same content, @message.content
  end

  def test_get_content
    @message_impl.
      should_receive(:getContent).
      and_return("foo")
    @message_impl.
      should_receive(:getContentType).
      and_return(String)

    assert_equal "foo", @message.content
  end

  def test_get_content_with_array
    decoded = ["foo", "bar"]

    @message_impl.
      should_receive(:getContent).
      and_return("[foo,bar]")
    @message_impl.
      should_receive(:getContentType).
      and_return("amqp/list")
    @messaging.
      should_receive(:decode).
      once.
      with(@message, "amqp/list").
      and_return(decoded)

    result = @message.content
    assert_same decoded, result
  end

  def test_get_content_size
    @message_impl.
      should_receive(:getContentSize).
      once.
      and_return(68)

    assert_equal 68, @message.content_size
  end

end

