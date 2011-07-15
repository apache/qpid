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

require 'qpid/sender'

class TestSender < Test::Unit::TestCase

  def setup
    @messaging = flexmock(Qpid::Messaging)
    @message = flexmock("message")

    @session_impl = flexmock("session_impl")

    @sender_impl = flexmock("sender_impl")
    @other_sender_impl = flexmock("other_sender_impl")
    @sender = Qpid::Messaging::Sender.new @sender_impl
    @other_sender = flexmock("other_sender")
  end

  def test_send
    message_impl = "message_impl"
    content = {:foo => :bar}
    @message.
      should_receive(:message_impl).
      once.
      and_return(message_impl)
    @sender_impl.
      should_receive(:send).
      once.
      with(message_impl, false)

    @sender.send @message
  end

  def test_send_and_dont_block
    message_impl = "message_impl"
    content = {:foo => :bar}
    @message.
      should_receive(:message_impl).
      once.
      and_return(message_impl)
    @sender_impl.
      should_receive(:send).
      once.
      with(message_impl, false)

    @sender.send @message, :block => false
  end

  def test_send_and_block
    message_impl = "message_impl"
    content = {:foo => :bar}
    @message.
      should_receive(:message_impl).
      once.
      and_return(message_impl)
    @sender_impl.
      should_receive(:send).
      once.
      with(message_impl, true)

    @sender.send @message, :block => true
  end

  def test_close
    @sender_impl.
      should_receive(:close).
      once

    @sender.close
  end

  def test_set_capacity
    @sender_impl.
      should_receive(:setCapacity).
      once.
      with(17)

    @sender.capacity = 17
  end

  def test_get_capacity
    @sender_impl.
      should_receive(:getCapacity).
      once.
      and_return(12)

    assert_equal 12, @sender.capacity
  end

  def test_unsettled
    @sender_impl.
      should_receive(:getUnsettled).
      once.
      and_return(5)

    assert_equal 5, @sender.unsettled
  end

  def test_available
    @sender_impl.
      should_receive(:getAvailable).
      once.
      and_return(15)

    assert_equal 15, @sender.available
  end

  def test_name
    @sender_impl.
      should_receive(:getName).
      once.
      and_return("myname")

    assert_equal "myname", @sender.name
  end

  def test_session
    @sender_impl.
      should_receive(:getSession).
      once.
      and_return(@session_impl)

    result = @sender.session

    assert_not_nil result
    assert_same @session_impl, result.session_impl
  end

  def test_is_valid
    @sender_impl.
      should_receive(:isValid).
      once.
      and_return(true)

    assert @sender.valid?
  end

  def test_is_null
    @sender_impl.
      should_receive(:isNull).
      once.
      and_return(false)

    assert !@sender.null?
  end

  def test_swap
    @other_sender.
      should_receive(:sender_impl).
      once.
      and_return(@other_sender_impl)
    @sender_impl.
      should_receive(:swap).
      once.
      with(@other_sender_impl)

    @sender.swap @other_sender
  end

end

