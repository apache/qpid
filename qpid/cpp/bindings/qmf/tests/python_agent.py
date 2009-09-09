#!/usr/bin/env python
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


import qmf
import sys
import time


class Model:
    # attr_reader :parent_class, :child_class
    def __init__(self):
        self.parent_class = qmf.SchemaObjectClass("org.apache.qpid.qmf", "parent")
        self.parent_class.add_property(qmf.SchemaProperty("name", qmf.TYPE_SSTR, {"index":True}))
        self.parent_class.add_property(qmf.SchemaProperty("state", qmf.TYPE_SSTR))
        
        self.parent_class.add_property(qmf.SchemaProperty("uint64val", qmf.TYPE_UINT64))
        self.parent_class.add_property(qmf.SchemaProperty("uint32val", qmf.TYPE_UINT32))
        self.parent_class.add_property(qmf.SchemaProperty("uint16val", qmf.TYPE_UINT16))
        self.parent_class.add_property(qmf.SchemaProperty("uint8val", qmf.TYPE_UINT8))
        
        self.parent_class.add_property(qmf.SchemaProperty("int64val", qmf.TYPE_INT64))
        self.parent_class.add_property(qmf.SchemaProperty("int32val", qmf.TYPE_INT32))
        self.parent_class.add_property(qmf.SchemaProperty("int16val", qmf.TYPE_INT16))
        self.parent_class.add_property(qmf.SchemaProperty("int8val", qmf.TYPE_INT8))
        
        self.parent_class.add_statistic(qmf.SchemaStatistic("queryCount", qmf.TYPE_UINT32, {"unit":"query", "desc":"Query count"}))
        
        _method = qmf.SchemaMethod("echo", {"desc":"Check responsiveness of the agent object"})
        _method.add_argument(qmf.SchemaArgument("sequence", qmf.TYPE_UINT32, {"dir":qmf.DIR_IN_OUT}))
        self.parent_class.add_method(_method)
        
        _method = qmf.SchemaMethod("set_numerics", {"desc":"Set the numeric values in the object"})
        _method.add_argument(qmf.SchemaArgument("test", qmf.TYPE_SSTR, {"dir":qmf.DIR_IN}))
        self.parent_class.add_method(_method)
        
        _method = qmf.SchemaMethod("create_child", {"desc":"Create a new child object"})
        _method.add_argument(qmf.SchemaArgument("child_name", qmf.TYPE_LSTR, {"dir":qmf.DIR_IN}))
        _method.add_argument(qmf.SchemaArgument("child_ref", qmf.TYPE_REF, {"dir":qmf.DIR_OUT}))
        self.parent_class.add_method(_method)
        
        _method = qmf.SchemaMethod("probe_userid", {"desc":"Return the user-id for this method call"})
        _method.add_argument(qmf.SchemaArgument("userid", qmf.TYPE_SSTR, {"dir":qmf.DIR_OUT}))
        self.parent_class.add_method(_method)

        self.child_class = qmf.SchemaObjectClass("org.apache.qpid.qmf", "child")
        self.child_class.add_property(qmf.SchemaProperty("name", qmf.TYPE_SSTR, {"index":True}))
        
    
    def register(self, agent):
        agent.register_class(self.parent_class)
        agent.register_class(self.child_class)



class App(qmf.AgentHandler):
    def get_query(self, context, query, userId):
        #    puts "Query: user=#{userId} context=#{context} class=#{query.class_name} object_num=#{query.object_id.object_num_low if query.object_id}"
        self._parent.inc_attr("queryCount")
        if query.class_name() == 'parent':
            self._agent.query_response(context, self._parent)
        elif query.object_id() == self._parent_oid:
            self._agent.query_response(context, self._parent)
        self._agent.query_complete(context)
    
    
    def method_call(self, context, name, object_id, args, userId):
        #    puts "Method: user=#{userId} context=#{context} method=#{name} object_num=#{object_id.object_num_low if object_id} args=#{args}"
        # oid = self._agent.alloc_object_id(2)
        # args['child_ref'] = oid
        # self._child = qmf.QmfObject(self._model.child_class)
        # self._child.set_attr("name", args.by_key("child_name"))
        # self._child.set_object_id(oid)
        # self._agent.method_response(context, 0, "OK", args)
        if name == "echo":
            self._agent.method_response(context, 0, "OK", args)
            
        elif name == "set_numerics":
            _retCode = 0
            _retText = "OK"
            
            if args['test'] == "big":
                self._parent.set_attr("uint64val", 0x9494949449494949)
                self._parent.set_attr("uint32val", 0xa5a55a5a)
                self._parent.set_attr("uint16val", 0xb66b)
                self._parent.set_attr("uint8val",  0xc7)
                
                self._parent.set_attr("int64val", 1000000000000000000)
                self._parent.set_attr("int32val", 1000000000)
                self._parent.set_attr("int16val", 10000)
                self._parent.set_attr("int8val",  100)
                
            elif args['test'] == "small":
                self._parent.set_attr("uint64val", 4)
                self._parent.set_attr("uint32val", 5)
                self._parent.set_attr("uint16val", 6)
                self._parent.set_attr("uint8val",  7)
                
                self._parent.set_attr("int64val", 8)
                self._parent.set_attr("int32val", 9)
                self._parent.set_attr("int16val", 10)
                self._parent.set_attr("int8val",  11)
                
            elif args['test'] == "negative":
                self._parent.set_attr("uint64val", 0)
                self._parent.set_attr("uint32val", 0)
                self._parent.set_attr("uint16val", 0)
                self._parent.set_attr("uint8val",  0)
                
                self._parent.set_attr("int64val", -10000000000)
                self._parent.set_attr("int32val", -100000)
                self._parent.set_attr("int16val", -1000)
                self._parent.set_attr("int8val",  -100)
                
            else:
                _retCode = 1
                _retText = "Invalid argument value for test"
            
            self._agent.method_response(context, _retCode, _retText, args)
            
        elif name == "create_child":
            _oid = self._agent.alloc_object_id(2)
            args['child_ref'] = _oid
            self._child = qmf.QmfObject(self._model.child_class)
            self._child.set_attr("name", args["child_name"])
            self._child.set_object_id(_oid)
            self._agent.method_response(context, 0, "OK", args)
            
        elif name == "probe_userid":
            args['userid'] = userId
            self._agent.method_response(context, 0, "OK", args)
            
        else:
            self._agent.method_response(context, 1, "Unimplemented Method: %s" % name, args)
    
    
    def main(self):
        self._settings = qmf.ConnectionSettings()
        if len(sys.argv) > 1:
            self._settings.set_attr("host", sys.argv[1])
        if len(sys.argv) > 2:
            self._settings.set_attr("port", int(sys.argv[2]))
        self._connection = qmf.Connection(self._settings)
        self._agent = qmf.Agent(self)
        
        self._model = Model()
        self._model.register(self._agent)
        
        self._agent.set_connection(self._connection)
        
        self._parent = qmf.QmfObject(self._model.parent_class)
        self._parent.set_attr("name", "Parent One")
        self._parent.set_attr("state", "OPERATIONAL")
        
        self._parent.set_attr("uint64val", 0)
        self._parent.set_attr("uint32val", 0)
        self._parent.set_attr("uint16val", 0)
        self._parent.set_attr("uint8val",  0)
        
        self._parent.set_attr("int64val", 0)
        self._parent.set_attr("int32val", 0)
        self._parent.set_attr("int16val", 0)
        self._parent.set_attr("int8val",  0)
        
        self._parent_oid = self._agent.alloc_object_id(1)
        self._parent.set_object_id(self._parent_oid)
        
        while True:     # there may be a better way, but
            time.sleep(1000) # I'm a python noob...



app = App()
app.main()

