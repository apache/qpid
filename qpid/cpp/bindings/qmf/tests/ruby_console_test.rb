#!/usr/bin/ruby

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

require 'test_base'

class ConsoleTest < ConsoleTestBase

  def test_A_agent_presence
    agents = []
    count = 0
    while agents.size == 0
      agents = @qmfc.get_objects(Qmf::Query.new(:class => "agent"))
      sleep(1)
      count += 1
      fail("Timed out waiting for remote agent") if count > 10
    end

    agentList = @qmfc.get_agents
    assert_equal(agentList.size, 2, "Number of agents reported by Console")
  end

  def test_B_basic_method_invocation
    parents = @qmfc.get_objects(Qmf::Query.new(:class => "parent"))
    assert_equal(parents.size, 1, "Number of 'parent' objects")
    parent = parents[0]
    for seq in 0...10
      result = parent.echo(seq)
      assert_equal(result.status, 0, "Method Response Status")
      assert_equal(result.text, "OK", "Method Response Text")
      assert_equal(result.args["sequence"], seq, "Echo Response Sequence")
    end

    result = parent.set_numerics("bogus")
    assert_equal(result.status, 1)
    assert_equal(result.text, "Invalid argument value for test")
  end

  def test_C_basic_types_numeric_big
    parents = @qmfc.get_objects(Qmf::Query.new(:class =>"parent"))
    assert_equal(parents.size, 1, "Number of parent objects")
    parent = parents[0]

    result = parent.set_numerics("big")
    assert_equal(result.status, 0, "Method Response Status")
    assert_equal(result.text, "OK", "Method Response Text")

    parent.update

    assert_equal(parent.uint64val, 0x9494949449494949)
    assert_equal(parent.uint32val, 0xA5A55A5A)
    assert_equal(parent.uint16val, 0xB66B)
    assert_equal(parent.uint8val,  0xC7)

    assert_equal(parent.int64val, 1000000000000000000)
    assert_equal(parent.int32val, 1000000000)
    assert_equal(parent.int16val, 10000)
    assert_equal(parent.int8val,  100)
  end

  def test_C_basic_types_numeric_small
    parents = @qmfc.get_objects(Qmf::Query.new(:class =>"parent"))
    assert_equal(parents.size, 1, "Number of parent objects")
    parent = parents[0]

    result = parent.set_numerics("small")
    assert_equal(result.status, 0, "Method Response Status")
    assert_equal(result.text, "OK", "Method Response Text")

    parent.update

    assert_equal(parent.uint64val, 4)
    assert_equal(parent.uint32val, 5)
    assert_equal(parent.uint16val, 6)
    assert_equal(parent.uint8val,  7)

    assert_equal(parent.int64val, 8)
    assert_equal(parent.int32val, 9)
    assert_equal(parent.int16val, 10)
    assert_equal(parent.int8val,  11)
  end

  def _test_C_basic_types_numeric_negative
    parents = @qmfc.get_objects(Qmf::Query.new(:class =>"parent"))
    assert_equal(parents.size, 1, "Number of parent objects")
    parent = parents[0]

    result = parent.set_numerics("negative")
    assert_equal(result.status, 0, "Method Response Status")
    assert_equal(result.text, "OK", "Method Response Text")

    parent.update

    assert_equal(parent.uint64val, 0)
    assert_equal(parent.uint32val, 0)
    assert_equal(parent.uint16val, 0)
    assert_equal(parent.uint8val,  0)

    assert_equal(parent.int64val, -10000000000)
    assert_equal(parent.int32val, -100000)
    assert_equal(parent.int16val, -1000)
    assert_equal(parent.int8val,  -100)
  end

  def _test_D_userid_for_method
    parents = @qmfc.get_objects(Qmf::Query.new(:class => "parent"))
    assert_equal(parents.size, 1, "Number of parent objects")
    parent = parents[0]

    result = parent.probe_userid
    assert_equal(result.status, 0, "Method Response Status")
    assert_equal(result.args["userid"], "guest")
  end

end

app = ConsoleTest.new



