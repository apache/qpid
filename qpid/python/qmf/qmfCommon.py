
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
import sys
import socket
import os
import time
import logging
from threading import Lock
from threading import Condition
try:
    import hashlib
    _md5Obj = hashlib.md5
except ImportError:
    import md5
    _md5Obj = md5.new




##
## Constants
##

AMQP_QMF_TOPIC = "amq.topic"
AMQP_QMF_DIRECT = "amq.direct"
AMQP_QMF_NAME_SEPARATOR = "/"
AMQP_QMF_AGENT_LOCATE = "amq.topic/agent.locate"
AMQP_QMF_AGENT_INDICATION = "amq.topic/agent.ind"


##==============================================================================
## Agent Identification
##==============================================================================

class AgentId(object):
    """
    Uniquely identifies a management agent within the entire management domain.
    
    Map format:
    map["vendor"] = str, name of vendor of the agent
    map["product"] = str, name of product using agent
    map["name"] = str, name of agent, unique within vendor and product.
    """
    _separator = ":"
    def __init__(self, vendor, product, name):
        """
        Note: this object must be immutable, as it is used to index into a dictionary
        """
        self._vendor = vendor
        self._product = product
        self._name = name

    def vendor(self):
        return self._vendor

    def product(self):
        return self._product

    def name(self):
        return self._name

    def mapEncode(self):
        _map = {}
        _map["vendor"] = self._vendor
        _map["product"] = self._product
        _map["name"] = self._name
        return _map

    def __cmp__(self, other):
        if not isinstance(other, AgentId) :
            raise TypeError("Invalid types for compare")
            # return 1
        me = str(self)
        them = str(other)

        if me < them:
            return -1
        if me > them:
            return 1
        return 0

    def __hash__(self):
        return (self._vendor, self._product, self._name).__hash__()

    def __repr__(self):
        return self._vendor + AgentId._separator + \
            self._product + AgentId._separator + \
            self._name

    def __str__(self):
        return self.__repr__()



def AgentIdFactory( param ):
    """
    Factory for constructing an AgentId class from various sources.

    @type param: various
    @param param: object to use for constructing AgentId
    @rtype: AgentId
    @returns: a new AgentId instance
    """
    if type(param) == str:
        if param.count(AgentId._separator) < 2:
            raise TypeError("AgentId string format must be 'vendor:product:name'")
        vendor, product, name = param.split(AgentId._separator)
        return AgentId(vendor, product, name)
    if type(param) == dict:
        # construct from map encoding
        if not "vendor" in param:
            raise TypeError("requires 'vendor' value")
        if not "product" in param:
            raise TypeError("requires 'product' value")
        if not "name" in param:
            raise TypeError("requires 'name' value")
        return AgentId( param["vendor"], param["product"], param["name"] )
    raise TypeError("Invalid type for AgentId construction")



##==============================================================================
## DATA MODEL
##==============================================================================



class ObjectId(object):
    """
    An instance of managed object must be uniquely identified within the
    management system.  Each managed object is given a key that is unique
    within the domain of the object's managing Agent.  Note that these
    keys are not unique across Agents.  Therefore, a globally unique name
    for an instance of a managed object is the concatenation of the
    object's key and the managing Agent's AgentId.

    Map format:
    map["agent_id"] = map representation of AgentId
    map["primary_key"] = str, key for managed object
    """
    def __init__(self, agentid, name):
        if not isinstance(agentid, AgentId):
            raise TypeError("requires an AgentId class")
        self._agentId = agentid;
        self._name = name;

    def getAgentId(self):
        """
        @rtype: class AgentId
        @returns: Id of agent that manages the object.
        """
        return self._agentId

    def getPrimaryKey(self):
        """
        @rtype: str
        @returns: Key of managed object.
        """
        return self._name

    def mapEncode(self):
        _map = {}
        _map["agent_id"] = self._agentId.mapEncode()
        _map["primary_key"] = self._name
        return _map

    def __repr__(self):
        return "%s:%s" % (self._agentId, self._name)

    def __cmp__(self, other):    
        if not isinstance(other, ObjectId) :
            raise TypeError("Invalid types for compare")

        if self._agentId < other._agentId:
            return -1
        if self._agentId > other._agentId:
            return 1
        if self._name < other._name:
            return -1
        if self._name > other._name:
            return 1
        return 0

    def __hash__(self):
        return (hash(self._agentId), self._name).__hash__()


def ObjectIdFactory( param ):
    """
    Factory for constructing ObjectIds from various sources

    @type param: various
    @param param: object to use for constructing ObjectId
    @rtype: ObjectId
    @returns: a new ObjectId instance
    """
    if type(param) == dict:
        # construct from map
        if "agent_id" not in param:
            raise TypeError("requires 'agent_id' value")
        if "primary_key" not in param:
            raise TypeError("requires 'primary_key' value")

        return ObjectId( AgentIdFactory(param["agent_id"]), param["primary_key"] )

    else:
        raise TypeError("Invalid type for ObjectId construction")




class QmfData(object):
    """
    Base data class representing arbitrarily structure data.  No schema or 
    managing agent is associated with data of this class.

    Map format:
    map["properties"] = map of unordered "name"=<value> pairs (optional)
    """
    def __init__(self, _props={}, _const=False):
        """
        @type _props: dict
        @param _props: dictionary of initial name=value pairs for object's property data.
        @type _const: boolean
        @param _const: if true, this object cannot be modified
        """
        self._properties = _props.copy()
        self._const = _const
        self._managed = False  # not managed by an Agent
        self._described = False  # not described by a schema

    def isManaged(self):
        return self._managed

    def isDescribed(self):
        return self._described

    def getProperties(self):
        return self._properties.copy()

    def getProperty(self, _name):
        return self._properties[_name]

    def setProperty(self, _name, _value):
        if self._const:
            raise Exception("cannot modify constant data object")
        self._properties[_name] = _value
        return _value

    def mapEncode(self):
        return self._properties.copy()

    def __repr__(self):
        return str(self.mapEncode())

    def __setattr__(self, _name, _value):
        # ignore private data members
        if _name[0] == '_':
            return super.__setattr__(self, _name, _value)
        # @todo: this is bad - what if the same name is used for a
        # property and statistic or argument?
        if _name in self._properties:
            return self.setProperty(_name, _value)
        return super.__setattr__(self, _name, _value)

    def __getattr__(self, _name):
        # @todo: this is bad - what if the same name is used for a
        # property and statistic or argument?
        if _name in self._properties: return self.getProperty(_name)
        raise AttributeError("no item named '%s' in this object" % _name)

    def __getitem__(self, _name):
        return self.__getattr__(_name)

    def __setitem__(self, _name, _value):
        return self.__setattr__(_name, _value)


def QmfDataFactory( param, const=False ):
    """
    Factory for constructing an QmfData class from various sources.

    @type param: various
    @param param: object to use for constructing QmfData instance
    @rtype: QmfData
    @returns: a new QmfData instance
    """
    if type(param) == dict:
        # construct from map
        return QmfData( _props=param, _const=const )

    else:
        raise TypeError("Invalid type for QmfData construction")



class QmfDescribed(QmfData):
    """
    Data that has a formally defined structure is represented by the
    QmfDescribed class.  This class extends the QmfData class by
    associating the data with a formal schema (SchemaObjectClass).

    Map format:
    map["schema_id"] = map representation of a SchemaClassId instance
    map["properties"] = map representation of a QmfData instance
    """
    def __init__(self, _schema=None, _schemaId=None, _props={}, _const=False ):
        """
        @type _schema: class SchemaClass or derivative
        @param _schema: an instance of the schema used to describe this object.
        @type _schemaId: class SchemaClassId
        @param _schemaId: if schema instance not available, this is mandatory.
        @type _props: dict
        @param _props: dictionary of initial name=value pairs for object's property data.
        @type _const: boolean
        @param _const: if true, this object cannot be modified
        """
        super(QmfDescribed, self).__init__(_props, _const)
        self._validated = False
        self._described = True
        self._schema = _schema
        if _schema:
            self._schemaId = _schema.getClassId()
            if self._const:
                self._validate()
        else:
            if _schemaId:
                self._schemaId = _schemaId
            else:
                raise Exception("A SchemaClass or SchemaClassId must be provided")


    def getSchemaClassId(self): 
        """
        @rtype: class SchemaClassId
        @returns: the identifier of the Schema that describes the structure of the data.
        """
        return self._schemaId

    def setSchema(self, _schema):
        """
        @type _schema: class SchemaClass or derivative
        @param _schema: instance of schema used to describe the structure of the data.
        """
        if self._schemaId != _schema.getClassId():
            raise Exception("Cannot reset current schema to a different schema")
        oldSchema = self._schema
        self._schema = _schema
        if not oldSchema and self._const:
            self._validate()

    def getPrimaryKey(self): 
        """
        Get a string composed of the object's primary key properties.
        @rtype: str
        @returns: a string composed from primary key property values. 
        """
        if not self._schema: 
            raise Exception("schema not available")

        if not self._validated:
            self._validate()

        if self._schema._pkeyNames == 0:
            if len(self._properties) != 1:
                raise Exception("no primary key defined")
            return str(self._properties.values()[0])

        result = u""
        for pkey in self._schema._pkeyNames:
            if result != u"":
                result += u":"
            try:
                valstr = unicode(self._properties[pkey])
            except:
                valstr = u"<undecodable>"
            result += valstr
        return result


    def mapEncode(self):
        _map = {}
        _map["schema_id"] = self._schemaId.mapEncode()
        _map["properties"] = super(QmfDescribed, self).mapEncode()
        return _map

    def _validate(self):
        """
        Compares this object's data against the associated schema.  Throws an 
        exception if the data does not conform to the schema.
        """
        for name,val in self._schema._properties.iteritems():
            # @todo validate: type compatible with amqp_type?
            # @todo validate: primary keys have values
            if name not in self._properties:
                if val._isOptional:
                    # ok not to be present, put in dummy value
                    # to simplify access
                    self._properties[name] = None
                else:
                    raise Exception("Required property '%s' not present." % name)
        self._validated = True



def QmfDescribedFactory( param, _schema=None ):
    """
    Factory for constructing an QmfDescribed class from various sources.

    @type param: various
    @param param: object to use for constructing QmfDescribed instance
    @type _schema: SchemaClass
    @param _schema: instance of the SchemaClass that describes this instance
    @rtype: QmfDescribed
    @returns: a new QmfDescribed instance
    """
    if type(param) == dict:
        # construct from map
        if "schema_id" not in param:
            raise TypeError("requires 'schema_id' value")
        if "properties" not in param:
            raise TypeError("requires 'properties' value")

        return QmfDescribed( _schema=_schema, _schemaId=SchemaClassIdFactory( param["schema_id"] ),
                             _props = param["properties"] )
    else:
        raise TypeError("Invalid type for QmfDescribed construction")



class QmfManaged(QmfDescribed):
    """
    Data that has a formally defined structure, and for which each
    instance of the data is managed by a particular Agent is represented 
    by the QmfManaged class.  This class extends the QmfDescribed class by
    associating the described data with a particular Agent.

    Map format:
    map["object_id"] = map representation of an ObjectId value
    map["schema_id"] = map representation of a SchemaClassId instance
    map["qmf_data"] = map representation of a QmfData instance
    map["timestamps"] = array of AMQP timestamps. [0] = time of last update, 
    [1] = creation timestamp, [2] = deletion timestamp or zero.
    """
    _ts_update = 0
    _ts_create = 1
    _ts_delete = 2
    def __init__( self, _agentId=None, _schema=None, _schemaId=None,
                  _props={}, _const=False ):
        """
        @type _agentId: class AgentId
        @param _agentId: globally unique identifier of the managing agent.
        @type _schema: class SchemaClass or derivative
        @param _schema: an instance of the schema used to describe this object.
        @type _schemaId: class SchemaClassId
        @param _schemaId: if schema instance not available, this is mandatory.
        @type _props: dict
        @param _props: dictionary of initial name=value pairs for object's property data.
        @type _const: boolean
        @param _const: if true, this object cannot be modified
        """
        super(QmfManaged, self).__init__(_schema=_schema, _schemaId=_schemaId,
                                         _props=_props, _const=_const)
        self._managed = True
        self._agentId = _agentId
        # timestamp, in millisec since epoch UTC
        _ctime = long(time.time() * 1000)
        self._timestamps = [_ctime,_ctime,0]


    def getObjectId(self):
        """
        @rtype: class ObjectId
        @returns: the ObjectId that uniquely identifies this managed object.
        """
        return ObjectId(self._agentId, self.getPrimaryKey())

    def isDeleted(self): 
        return self._timestamps[QmfManaged._ts_delete] == 0

    def mapEncode(self):
        _map = super(QmfManaged, self).mapEncode()
        _map["agent_id"] = self._agentId.mapEncode()
        _map["timestamps"] = self._timestamps[:]
        return _map



def QmfManagedFactory( param, _schema=None ):
    """
    Factory for constructing an QmfManaged instance from various sources.

    @type param: various
    @param param: object to use for constructing QmfManaged instance
    @type _schema: SchemaClass
    @param _schema: instance of the SchemaClass that describes this instance
    @rtype: QmfManaged
    @returns: a new QmfManaged instance
    """
    if type(param) == dict:
        # construct from map
        if "agent_id" not in param:
            raise TypeError("requires 'agent_id' value")
        _qd = QmfDescribedFactory( param, _schema )

        return QmfManaged( _agentId = AgentIdFactory(param["agent_id"]),
                           _schema=_schema, _schemaId=_qd._schemaId,
                           _props = _qd._properties )
    else:
        raise TypeError("Invalid type for QmfManaged construction")



class QmfEvent(QmfDescribed):
    """
    A QMF Event is a type of described data that is not managed. Events are 
    notifications that are sent by Agents. An event notifies a Console of a 
    change in some aspect of the system under managment.
    """
    def __init__(self, _map=None,
                 _timestamp=None, _agentId=None, 
                 _schema=None, _schemaId=None,
                 _props={}, _const=False):
        """
        @type _map: dict
        @param _map: if not None, construct instance from map representation. 
        @type _timestamp: int
        @param _timestamp: moment in time when event occurred, expressed
               as milliseconds since Midnight, Jan 1, 1970 UTC.
        @type _agentId: class AgentId
        @param _agentId: Identifies agent issuing this event.
        @type _schema: class Schema
        @param _schema:
        @type _schemaId: class SchemaClassId (event)
        @param _schemaId: identi
        """
        if _map:
            if type(_map) != dict:
                raise TypeError("parameter '_map' must be of type 'dict'")
            if "timestamp" not in _map:
                pass
            if "agent_id" not in _map:
                pass
            _qe = QmfDescribedFactory( _map, _schema )
            super(QmfEvent, self).__init__( _schema=_qe._schema, _schemaId=_qe._schemaId,
                                            _props=_qe._properties, _const=_qe._const )
            self._timestamp = long(_map["timestamp"])
            self._agentId = AgentIdFactory(_map["agent_id"])
        else:
            super(QmfEvent, self).__init__(_schema=_schema, _schemaId=_schemaId,
                                           _props=_props, _const=_const)
            self._timestamp = long(_timestamp)
            self._agentId = _agentId

    def getTimestamp(self): return self._timestamp

    def getAgentId(self): return self._agentId

    def mapEncode(self):
        _map = super(QmfEvent, self).mapEncode()
        _map["timestamp"] = self._timestamp
        _map["agent_id"] = self._agentId.mapEncode()
        return _map



#==============================================================================
#==============================================================================
#==============================================================================



class QmfObject(object):
     # attr_reader :impl, :object_class
     def __init__(self, cls, kwargs={}):
         pass
#         self._cv = Condition()
#         self._sync_count = 0
#         self._sync_result = None
#         self._allow_sets = False
#         if kwargs.has_key("broker"):
#             self._broker = kwargs["broker"]
#         else:
#             self._broker = None
#         if cls:
#             self.object_class = cls
#             self.impl = qmfengine.Object(self.object_class.impl)
#         elif kwargs.has_key("impl"):
#             self.impl = qmfengine.Object(kwargs["impl"])
#             self.object_class = SchemaObjectClass(None,
#                                                   None,
#                                                   {"impl":self.impl.getClass()})
#         else:
#             raise Exception("Argument error: required parameter ('impl') not supplied")
    
    
#     def destroy(self):
#         self.impl.destroy()
    
    
#     def object_id(self):
#         return ObjectId(self.impl.getObjectId())
    
    
#     def set_object_id(self, oid):
#         self.impl.setObjectId(oid.impl)


#     def properties(self):
#         list = []
#         for prop in self.object_class.properties:
#             list.append([prop, self.get_attr(prop.name())])
#         return list


#     def statistics(self):
#         list = []
#         for stat in self.object_class.statistics:
#             list.append([stat, self.get_attr(stat.name())])
#         return list
    
    
#     def get_attr(self, name):
#         val = self._value(name)
#         vType = val.getType()
#         if vType == TYPE_UINT8:  return val.asUint()
#         elif vType == TYPE_UINT16:  return val.asUint()
#         elif vType == TYPE_UINT32:  return val.asUint()
#         elif vType == TYPE_UINT64:  return val.asUint64()
#         elif vType == TYPE_SSTR:  return val.asString()
#         elif vType == TYPE_LSTR:  return val.asString()
#         elif vType == TYPE_ABSTIME:  return val.asInt64()
#         elif vType == TYPE_DELTATIME:  return val.asUint64()
#         elif vType == TYPE_REF:  return ObjectId(val.asObjectId())
#         elif vType == TYPE_BOOL:  return val.asBool()
#         elif vType == TYPE_FLOAT:  return val.asFloat()
#         elif vType == TYPE_DOUBLE:  return val.asDouble()
#         elif vType == TYPE_UUID:  return val.asUuid()
#         elif vType == TYPE_INT8:  return val.asInt()
#         elif vType == TYPE_INT16:  return val.asInt()
#         elif vType == TYPE_INT32:  return val.asInt()
#         elif vType == TYPE_INT64:  return val.asInt64()
#         else:
#             # when TYPE_MAP
#             # when TYPE_OBJECT
#             # when TYPE_LIST
#             # when TYPE_ARRAY
#             logging.error( "Unsupported type for get_attr? '%s'" % str(val.getType()) )
#             return None
    
    
#     def set_attr(self, name, v):
#         val = self._value(name)
#         vType = val.getType()
#         if vType == TYPE_UINT8: return val.setUint(v)
#         elif vType == TYPE_UINT16: return val.setUint(v)
#         elif vType == TYPE_UINT32: return val.setUint(v)
#         elif vType == TYPE_UINT64: return val.setUint64(v)
#         elif vType == TYPE_SSTR:
#             if v: return val.setString(v)
#             else: return val.setString('')
#         elif vType == TYPE_LSTR:
#             if v: return val.setString(v) 
#             else: return val.setString('')
#         elif vType == TYPE_ABSTIME: return val.setInt64(v)
#         elif vType == TYPE_DELTATIME: return val.setUint64(v)
#         elif vType == TYPE_REF: return val.setObjectId(v.impl)
#         elif vType == TYPE_BOOL: return val.setBool(v)
#         elif vType == TYPE_FLOAT: return val.setFloat(v)
#         elif vType == TYPE_DOUBLE: return val.setDouble(v)
#         elif vType == TYPE_UUID: return val.setUuid(v)
#         elif vType == TYPE_INT8: return val.setInt(v)
#         elif vType == TYPE_INT16: return val.setInt(v)
#         elif vType == TYPE_INT32: return val.setInt(v)
#         elif vType == TYPE_INT64: return val.setInt64(v)
#         else:
#             # when TYPE_MAP
#             # when TYPE_OBJECT
#             # when TYPE_LIST
#             # when TYPE_ARRAY
#             logging.error("Unsupported type for get_attr? '%s'" % str(val.getType()))
#             return None
    
    
#     def __getitem__(self, name):
#         return self.get_attr(name)
    
    
#     def __setitem__(self, name, value):
#         self.set_attr(name, value)
    
    
#     def inc_attr(self, name, by=1):
#         self.set_attr(name, self.get_attr(name) + by)
    
    
#     def dec_attr(self, name, by=1):
#         self.set_attr(name, self.get_attr(name) - by)
    
    


#     def _invokeMethod(self, name, argMap):
#         """
#         Private: Helper function that invokes an object's method, and waits for the result.
#         """
#         self._cv.acquire()
#         try:
#             timeout = 30
#             self._sync_count = 1
#             self.impl.invokeMethod(name, argMap, self)
#             if self._broker:
#                 self._broker.conn.kick()
#             self._cv.wait(timeout)
#             if self._sync_count == 1:
#                 raise Exception("Timed out: waiting for response to method call.")
#         finally:
#             self._cv.release()

#         return self._sync_result


#     def _method_result(self, result):
#         """
#         Called to return the result of a method call on an object
#         """
#         self._cv.acquire();
#         try:
#             self._sync_result = result
#             self._sync_count -= 1
#             self._cv.notify()
#         finally:
#             self._cv.release()


#     def _marshall(schema, args):
#         '''
#         Private: Convert a list of arguments (positional) into a Value object of type "map".
#         Used to create the argument parameter for an object's method invokation.
#         '''
#         # Build a map of the method's arguments
#         map = qmfengine.Value(TYPE_MAP)
#         for arg in schema.arguments:
#             if arg.direction == DIR_IN or arg.direction == DIR_IN_OUT:
#                 map.insert(arg.name, qmfengine.Value(arg.typecode))

#         # install each argument's value into the map
#         marshalled = Arguments(map)
#         idx = 0
#         for arg in schema.arguments:
#             if arg.direction == DIR_IN or arg.direction == DIR_IN_OUT:
#                 if args[idx]:
#                     marshalled[arg.name] = args[idx]
#                 idx += 1

#         return marshalled.map


#     def _value(self, name):
#         val = self.impl.getValue(name)
#         if not val:
#             raise Exception("Argument error: attribute named '%s' not defined for package %s, class %s" % 
#                             (name,
#                              self.object_class.impl.getClassKey().getPackageName(),
#                              self.object_class.impl.getClassKey().getClassName()))
#         return val





class Arguments(object):
    def __init__(self, map):
        pass
#         self.map = map
#         self._by_hash = {}
#         key_count = self.map.keyCount()
#         a = 0
#         while a < key_count:
#             self._by_hash[self.map.key(a)] = self.by_key(self.map.key(a))
#             a += 1
    
    
#     def __getitem__(self, key):
#         return self._by_hash[key]
    
    
#     def __setitem__(self, key, value):
#         self._by_hash[key] = value
#         self.set(key, value)
    
    
#     def __iter__(self):
#         return self._by_hash.__iter__


#     def __getattr__(self, name):
#         if name in self._by_hash:
#             return self._by_hash[name]
#         return super.__getattr__(self, name)


#     def __setattr__(self, name, value):
#         #
#         # ignore local data members
#         #
#         if (name[0] == '_' or
#             name == 'map'):
#             return super.__setattr__(self, name, value)

#         if name in self._by_hash:
#             self._by_hash[name] = value
#             return self.set(name, value)

#         return super.__setattr__(self, name, value)


#     def by_key(self, key):
#         val = self.map.byKey(key)
#         vType = val.getType()
#         if vType == TYPE_UINT8: return val.asUint()
#         elif vType == TYPE_UINT16: return val.asUint()
#         elif vType == TYPE_UINT32: return val.asUint()
#         elif vType == TYPE_UINT64: return val.asUint64()
#         elif vType == TYPE_SSTR: return val.asString()
#         elif vType == TYPE_LSTR: return val.asString()
#         elif vType == TYPE_ABSTIME:   return val.asInt64()
#         elif vType == TYPE_DELTATIME: return val.asUint64()
#         elif vType == TYPE_REF:  return ObjectId(val.asObjectId())
#         elif vType == TYPE_BOOL: return val.asBool()
#         elif vType == TYPE_FLOAT:  return val.asFloat()
#         elif vType == TYPE_DOUBLE: return val.asDouble()
#         elif vType == TYPE_UUID: return val.asUuid()
#         elif vType == TYPE_INT8: return val.asInt()
#         elif vType == TYPE_INT16: return val.asInt()
#         elif vType == TYPE_INT32: return val.asInt()
#         elif vType == TYPE_INT64: return val.asInt64()
#         else:
#             # when TYPE_MAP
#             # when TYPE_OBJECT
#             # when TYPE_LIST
#             # when TYPE_ARRAY
#             logging.error( "Unsupported Type for Get? '%s'" % str(val.getType()))
#             return None
    
    
#     def set(self, key, value):
#         val = self.map.byKey(key)
#         vType = val.getType()
#         if vType == TYPE_UINT8: return val.setUint(value)
#         elif vType == TYPE_UINT16: return val.setUint(value)
#         elif vType == TYPE_UINT32: return val.setUint(value)
#         elif vType == TYPE_UINT64: return val.setUint64(value)
#         elif vType == TYPE_SSTR: 
#             if value:
#                 return val.setString(value)
#             else:
#                 return val.setString('')
#         elif vType == TYPE_LSTR:
#             if value:
#                 return val.setString(value)
#             else:
#                 return val.setString('')
#         elif vType == TYPE_ABSTIME: return val.setInt64(value)
#         elif vType == TYPE_DELTATIME: return val.setUint64(value)
#         elif vType == TYPE_REF: return val.setObjectId(value.impl)
#         elif vType == TYPE_BOOL: return val.setBool(value)
#         elif vType == TYPE_FLOAT: return val.setFloat(value)
#         elif vType == TYPE_DOUBLE: return val.setDouble(value)
#         elif vType == TYPE_UUID: return val.setUuid(value)
#         elif vType == TYPE_INT8: return val.setInt(value)
#         elif vType == TYPE_INT16: return val.setInt(value)
#         elif vType == TYPE_INT32: return val.setInt(value)
#         elif vType == TYPE_INT64: return val.setInt64(value)
#         else:
#             # when TYPE_MAP
#             # when TYPE_OBJECT
#             # when TYPE_LIST
#             # when TYPE_ARRAY
#             logging.error("Unsupported Type for Set? '%s'" % str(val.getType()))
#             return None



class MethodResponse(object):
    def __init__(self, impl):
        pass
#         self.impl = qmfengine.MethodResponse(impl)


#     def status(self):
#         return self.impl.getStatus()


#     def exception(self):
#         return self.impl.getException()


#     def text(self):
#         return exception().asString()


#     def args(self):
#         return Arguments(self.impl.getArgs())


#     def __getattr__(self, name):
#         myArgs = self.args()
#         return myArgs.__getattr__(name)


#     def __setattr__(self, name, value):
#         if name == 'impl':
#             return super.__setattr__(self, name, value)

#         myArgs = self.args()
#         return myArgs.__setattr__(name, value)



#   ##==============================================================================
#   ## QUERY
#   ##==============================================================================


class Query:
    def __init__(self, kwargs={}):
        pass
#         if "impl" in kwargs:
#             self.impl = kwargs["impl"]
#         else:
#             package = ''
#             if "key" in kwargs:
#                 # construct using SchemaClassKey:
#                 self.impl = qmfengine.Query(kwargs["key"])
#             elif "object_id" in kwargs:
#                 self.impl = qmfengine.Query(kwargs["object_id"].impl)
#             else:
#                 if "package" in kwargs:
#                     package = kwargs["package"]
#                 if "class" in kwargs:
#                     self.impl = qmfengine.Query(kwargs["class"], package)
#                 else:
#                     raise Exception("Argument error: invalid arguments, use 'key', 'object_id' or 'class'[,'package']")


#     def package_name(self): return self.impl.getPackage()
#     def class_name(self): return self.impl.getClass()
#     def object_id(self):
#         _objid = self.impl.getObjectId()
#         if _objid:
#             return ObjectId(_objid)
#         else:
#             return None


##==============================================================================
## SCHEMA
##==============================================================================

# known schema types

SchemaTypeData = "data"
SchemaTypeEvent = "event"

# format convention for schema hash

_schemaHashStrFormat = "%08x-%08x-%08x-%08x"
_schemaHashStrDefault = "00000000-00000000-00000000-00000000"

# Argument typecodes, access, and direction qualifiers

class qmfTypes(object):
    TYPE_UINT8      = 1
    TYPE_UINT16     = 2
    TYPE_UINT32     = 3
    TYPE_UINT64     = 4
    TYPE_SSTR       = 6
    TYPE_LSTR       = 7
    TYPE_ABSTIME    = 8
    TYPE_DELTATIME  = 9
    TYPE_REF        = 10
    TYPE_BOOL       = 11
    TYPE_FLOAT      = 12
    TYPE_DOUBLE     = 13
    TYPE_UUID       = 14
    TYPE_MAP        = 15
    TYPE_INT8       = 16
    TYPE_INT16      = 17
    TYPE_INT32      = 18
    TYPE_INT64      = 19
    TYPE_OBJECT     = 20
    TYPE_LIST       = 21
    TYPE_ARRAY      = 22


class qmfAccess(object):
    READ_CREATE = 1 
    READ_WRITE = 2
    READ_ONLY = 3


class qmfDirection(object):
    DIR_IN = 1
    DIR_OUT = 2
    DIR_IN_OUT = 3



def _toBool( param ):
    """
    Helper routine to convert human-readable representations of
    boolean values to python bool types.
    """
    _false_strings = ["off", "no", "false", "0", "none"]
    _true_strings =  ["on",  "yes", "true", "1"]
    if type(param) == str:
        lparam = param.lower()
        if lparam in _false_strings:
            return False
        if lparam in _true_strings:
            return True
        raise TypeError("unrecognized boolean string: '%s'" % param )
    else:
        return bool(param)



class SchemaClassId(object):
    """ 
    Unique identifier for an instance of a SchemaClass.

    Map format:
    map["package_name"] = str, name of associated package
    map["class_name"] = str, name of associated class
    map["type"] = str, "data"|"event", default: "data"
    optional:
    map["hash_str"] = str, hash value in standard format or None 
    if hash is unknown.
    """
    def __init__(self, pname, cname, stype=SchemaTypeData, hstr=None):
        """
        @type pname: str
        @param pname: the name of the class's package
        @type cname: str
        @param cname: name of the class
        @type stype: str
        @param stype: schema type [SchemaTypeData | SchemaTypeEvent]
        @type hstr: str
        @param hstr: the hash value in '%08x-%08x-%08x-%08x' format
        """
        self._pname = pname
        self._cname = cname
        if stype != SchemaTypeData and stype != SchemaTypeEvent:
            raise TypeError("Invalid SchemaClassId type: '%s'" % stype)
        self._type = stype
        self._hstr = hstr
        if self._hstr:
            try:
                # sanity check the format of the hash string
                hexValues = hstr.split("-")
                h0 = int(hexValues[0], 16)
                h1 = int(hexValues[1], 16)
                h2 = int(hexValues[2], 16)
                h3 = int(hexValues[3], 16)
            except:
                raise Exception("Invalid SchemaClassId format: bad hash string: '%s':"
                                % hstr)

    def getPackageName(self): 
        """ 
        Access the package name in the SchemaClassId.

        @rtype: str
        """
        return self._pname


    def getClassName(self): 
        """ 
        Access the class name in the SchemaClassId

        @rtype: str
        """
        return self._cname


    def getHashString(self): 
        """ 
        Access the schema's hash as a string value

        @rtype: str
        """
        return self._hstr


    def getType(self):
        """ 
        Returns the type code associated with this Schema

        @rtype: str
        """
        return self._type

    def mapEncode(self):
        _map = {}
        _map["package_name"] = self._pname
        _map["class_name"] = self._cname
        _map["type"] = self._type
        if self._hstr: _map["hash_str"] = self._hstr
        return _map

    def __repr__(self):
        if self._type == SchemaTypeEvent:
            stype = "event"
        else:
            stype = "data"
        hstr = self.getHashString()
        if not hstr:
            hstr = _schemaHashStrDefault
        return self._pname + ":" + self._cname + ":" + stype +  "(" + hstr  + ")"


    def __cmp__(self, other):
        if not isinstance(other, SchemaClassId) :
            raise TypeError("Invalid types for compare")
            # return 1
        me = str(self)
        them = str(other)
        if me < them:
            return -1
        if me > them:
            return 1
        return 0


    def __hash__(self):
        return (self._pname, self._cname, self._hstr).__hash__()



def SchemaClassIdFactory( param ):
    """
    Factory for constructing SchemaClassIds from various sources

    @type param: various
    @param param: object to use for constructing SchemaClassId
    @rtype: SchemaClassId
    @returns: a new SchemaClassId instance
    """
    if type(param) == str:
        # construct from __repr__/__str__ representation
        try:
            pname, cname, rest = param.split(":")
            stype,rest = rest.split("(");
            hstr = rest.rstrip(")");
            if hstr == _schemaHashStrDefault:
                hstr = None
            return SchemaClassId( pname, cname, stype, hstr )
        except:
            raise TypeError("Invalid string format: '%s'" % param)

    if type(param) == dict:
        # construct from map representation
        if "package_name" in param:
            pname = param["package_name"]
        else:
            raise TypeError("'package_name' attribute is required")

        if "class_name" in param:
            cname = param["class_name"]
        else:
            raise TypeError("'class_name' attribute is required")

        hstr = None
        if "hash_str" in param:
            hstr = param["hash_str"]

        stype = "data"
        if "type" in param:
            stype = param["type"]

        return SchemaClassId( pname, cname, stype, hstr )

    else:
        raise TypeError("Invalid type for SchemaClassId construction")



class SchemaProperty(object):
    """
    Describes the structure of a Property data object.
    Map format:
    map["amqp_type"] = int, AMQP type code indicating property's data type
    
    optional:
    map["access"] = str, access allowed to this property, default "RO"
    map["index"] = bool, True if this property is an index value, default False
    map["optional"] = bool, True if this property is optional, default False
    map["unit"] = str, describes units used
    map["min"] = int, minimum allowed value
    map["max"] = int, maximun allowed value
    map["maxlen"] = int, if string type, this is the maximum length in bytes 
    required to represent the longest instance of this string.
    map["desc"] = str, human-readable description of this argument
    map["reference"] = str, ???
    map["parent_ref"] = bool, true if this property references an object  in
    which this object is in a child-parent relationship. Default False
    """
    __hash__ = None
    _access_strings = ["RO","RW","RC"]
    def __init__(self, typeCode, kwargs={}):
        self._type = typeCode
        self._access  = "RO"
        self._isIndex   = False
        self._isOptional = False
        self._unit    = None
        self._min     = None
        self._max     = None
        self._maxlen  = None
        self._desc    = None
        self._reference = None
        self._isParentRef  = False

        for key, value in kwargs.items():
            if key == "access":
                value = str(value).upper()
                if value not in self._access_strings:
                    raise TypeError("invalid value for access parameter: '%s':" % value )
                self._access = value
            elif key == "index"   : self._isIndex = _toBool(value)
            elif key == "optional": self._isOptional = _toBool(value)
            elif key == "unit"    : self._unit    = value
            elif key == "min"     : self._min     = value
            elif key == "max"     : self._max     = value
            elif key == "maxlen"  : self._maxlen  = value
            elif key == "desc"    : self._desc    = value
            elif key == "reference" : self._reference = value
            elif key == "parent_ref"   : self._isParentRef = _toBool(value)

    def getType(self): return self._type

    def getAccess(self): return self._access

    def isOptional(self): return self._isOptional

    def isIndex(self): return self._isIndex

    def getUnit(self): return self._unit

    def getMin(self): return self._min

    def getMax(self): return self._max

    def getMaxLen(self): return self._maxlen

    def getDesc(self): return self._desc

    def getReference(self): return self._reference

    def isParentRef(self): return self._isParentRef

    def mapEncode(self):
        """
        Return the map encoding of this schema.
        """
        _map = {}
        _map["amqp_type"] = self._type
        _map["access"] = self._access
        _map["index"] = self._isIndex
        _map["optional"] = self._isOptional
        if self._unit: _map["unit"] = self._unit
        if self._min:  _map["min"] = self._min
        if self._max:  _map["max"] = self._max
        if self._maxlen: _map["maxlen"] = self._maxlen
        if self._desc: _map["desc"] = self._desc
        if self._reference: _map["reference"] = self._reference
        _map["parent_ref"] = self._isParentRef
        return _map

    def __repr__(self): return str(self.mapEncode())

    def _updateHash(self, hasher):
        """
        Update the given hash object with a hash computed over this schema.
        """
        hasher.update(str(self._type))
        hasher.update(str(self._isIndex))
        hasher.update(str(self._isOptional))
        if self._access: hasher.update(self._access)
        if self._unit: hasher.update(self._unit)
        if self._desc: hasher.update(self._desc)



def SchemaPropertyFactory( _map ):
    """
    Factory for constructing SchemaProperty from a map

    @type _map: dict
    @param _map: from mapEncode() of SchemaProperty
    @rtype: SchemaProperty
    @returns: a new SchemaProperty instance
    """
    if type(_map) == dict:
        # construct from map
        if "amqp_type" not in _map:
            raise TypeError("requires 'amqp_type' value")

        return SchemaProperty( _map["amqp_type"], _map )

    else:
        raise TypeError("Invalid type for SchemaProperty construction")




class SchemaArgument(object):
    """
    Describes the structure of an Argument to a Method call.
    Map format:
    map["amqp_type"] = int, type code indicating argument's data type
    map["dir"] = str, direction for an argument associated with a 
    Method, "I"|"O"|"IO", default value: "I"
    optional:
    map["desc"] = str, human-readable description of this argument
    map["default"] = by amqp_type, default value to use if none provided
    """
    __hash__ = None
    _dir_strings = ["I", "O", "IO"]
    def __init__(self, typeCode, kwargs={}):
        self._type = typeCode
        self._dir   = "I"
        self._desc    = None
        self._default = None

        for key, value in kwargs.items():
            if key == "dir":
                value = str(value).upper()
                if value not in self._dir_strings:
                    raise TypeError("invalid value for dir parameter: '%s'" % value)
                self._dir = value
            elif key == "desc"    : self._desc    = value
            elif key == "default" : self._default = value

    def getType(self): return self._type

    def getDirection(self): return self._dir

    def getDesc(self): return self._desc

    def getDefault(self): return self._default

    def mapEncode(self):
        """
        Return the map encoding of this schema.
        """
        _map = {}
        _map["amqp_type"] = self._type
        _map["dir"] = self._dir
        # optional:
        if self._default: _map["default"] = self._default
        if self._desc: _map["desc"] = self._desc
        return _map

    def __repr__(self): return str(self.mapEncode())

    def _updateHash(self, hasher):
        """
        Update the given hash object with a hash computed over this schema.
        """
        hasher.update(str(self._type))
        hasher.update(self._dir)
        if self._desc: hasher.update(self._desc)



def SchemaArgumentFactory( param ):
    """
    Factory for constructing SchemaArguments from various sources

    @type param: various
    @param param: object to use for constructing SchemaArgument
    @rtype: SchemaArgument
    @returns: a new SchemaArgument instance
    """
    if type(param) == dict:
        # construct from map
        if not "amqp_type" in param:
            raise TypeError("requires 'amqp_type' value")

        return SchemaArgument( param["amqp_type"], param )

    else:
        raise TypeError("Invalid type for SchemaArgument construction")



class SchemaMethod(object):
    """ 
    The SchemaMethod class describes the method's structure, and contains a
    SchemaProperty class for each argument declared by the method.

    Map format:
    map["arguments"] = map of "name"=<SchemaArgument> pairs.
    map["desc"] = str, description of the method
    """
    def __init__(self, args={}, _desc=None):
        """
        Construct a SchemaMethod.

        @type args: map of "name"=<SchemaProperty> objects
        @param args: describes the arguments accepted by the method
        @type _desc: str
        @param _desc: Human-readable description of the schema
        """
        self._arguments = args.copy()
        self._desc = _desc

    def getDesc(self): return self._desc

    def getArgCount(self): return len(self._arguments)

    def getArguments(self): return self._arguments.copy()

    def getArgument(self, name): return self._arguments[name]

    def addArgument(self, name, schema):
        """
        Add an argument to the list of arguments passed to this method.  
        Used by an agent for dynamically creating method schema.

        @type name: string
        @param name: name of new argument
        @type schema: SchemaProperty
        @param schema: SchemaProperty to add to this method
        """
        if not isinstance(schema, SchemaProperty):
            raise TypeError("argument must be a SchemaProperty class")
        self._arguments[name] = schema

    def mapEncode(self):
        """
        Return the map encoding of this schema.
        """
        _map = {}
        _args = {}
        for name,val in self._arguments.iteritems():
            _args[name] = val.mapEncode()
        _map["arguments"] = _args
        if self._desc: _map["desc"] = self._desc
        return _map

    def __repr__(self):
        result = "("
        first = True
        for name,arg in self._arguments.iteritems():
            if arg._dir.find("I") != -1:
                if first:
                    first = False
                else:
                    result += ", "
                result += name
        result += ")"
        return result

    def _updateHash(self, hasher):
        """
        Update the given hash object with a hash computed over this schema.
        """
        for name,val in self._arguments.iteritems():
            hasher.update(name)
            val._updateHash(hasher)
        if self._desc: hasher.update(self._desc)



def SchemaMethodFactory( param ):
    """
    Factory for constructing a SchemaMethod from various sources

    @type param: various
    @param param: object to use for constructing a SchemaMethod
    @rtype: SchemaMethod
    @returns: a new SchemaMethod instance
    """
    if type(param) == dict:
        # construct from map
        args = {}
        desc = None
        if "arguments" in param:
            for name,val in param["arguments"].iteritems():
                args[name] = SchemaArgumentFactory(val)
        if "desc" in param:
            desc = param["desc"]

        return SchemaMethod( args, desc )

    else:
        raise TypeError("Invalid type for SchemaMethod construction")



class SchemaClass(object):
    """
    Base class for Data and Event Schema classes.
    """
    def __init__(self, pname, cname, stype, desc=None, hstr=None):
        """
        Schema Class constructor.

        @type pname: str
        @param pname: package name
        @type cname: str
        @param cname: class name
        @type stype: str
        @param stype: type of schema, either "data" or "event"
        @type desc: str
        @param desc: Human-readable description of the schema
        @type hstr: str
        @param hstr: hash computed over the schema body, else None
        """
        self._pname = pname
        self._cname = cname
        self._type = stype
        self._desc = desc
        if hstr:
            self._classId = SchemaClassId( pname, cname, stype, hstr )
        else:
            self._classId = None
        self._properties = {}
        self._methods = {}
        self._pkeyNames = []


    def getClassId(self): 
        if not self._classId:
            self.generateHash()
        return self._classId

    def getDesc(self): return self._desc

    def generateHash(self): 
        """
        generate an md5 hash over the body of the schema,
        and return a string representation of the hash
        in format "%08x-%08x-%08x-%08x"
        """
        md5Hash = _md5Obj()
        md5Hash.update(self._pname)
        md5Hash.update(self._cname)
        md5Hash.update(self._type)
        for name,x in self._properties.iteritems():
            md5Hash.update(name)
            x._updateHash( md5Hash )
        for name,x in self._methods.iteritems():
            md5Hash.update(name)
            x._updateHash( md5Hash )
        idx = 0
        for name in self._pkeyNames:
            md5Hash.update(str(idx) + name)
            idx += 1
        hstr = md5Hash.hexdigest()[0:8] + "-" +\
            md5Hash.hexdigest()[8:16] + "-" +\
            md5Hash.hexdigest()[16:24] + "-" +\
            md5Hash.hexdigest()[24:32]
        # update classId with new hash value
        self._classId = SchemaClassId( self._pname,
                                       self._cname,
                                       self._type,
                                       hstr )
        return hstr


    def mapEncode(self):
        """
        Return the map encoding of this schema.
        """
        _map = {}
        _map["schema_id"] = self.getClassId().mapEncode()
        _map["desc"] = self._desc
        if len(self._properties):
            _props = {}
            for name,val in self._properties.iteritems():
                _props[name] = val.mapEncode()
            _map["properties"] = _props

        if len(self._methods):
            _meths = {}
            for name,val in self._methods.iteritems():
                _meths[name] = val.mapEncode()
            _map["methods"] = _meths

        if len(self._pkeyNames):
            _map["primary_key"] = self._pkeyNames[:]
        return _map

    def __repr__(self):
        return str(self.getClassId())



class SchemaObjectClass(SchemaClass):
    """
    A schema class that describes a data object.  The data object is composed 
    of zero or more properties and methods.  An instance of the SchemaObjectClass
    can be identified using a key generated by concantenating the values of
    all properties named in the primary key list.
    
    Map format:
    map["schema_id"] = map, SchemaClassId map for this object.
    map["desc"] = human readable description of this schema.
    map["primary_key"] = ordered list of property names used to construct the Primary Key"
    map["properties"] = map of "name":SchemaProperty instances.
    map["methods"] = map of "name":SchemaMethods instances.
    """
    def __init__( self, pname, cname, desc=None, _hash=None, 
                  _props={}, _pkey=[], _methods={}): 
        """
        @type pname: str
        @param pname: name of package this schema belongs to
        @type cname: str
        @param cname: class name for this schema
        @type desc: str
        @param desc: Human-readable description of the schema
        @type _hash: str
        @param _methods: hash computed on the body of this schema, if known
        @type _props: map of 'name':<SchemaProperty> objects
        @param _props: all properties provided by this schema
        @type _pkey: list of strings
        @param _pkey: names of each property to be used for constructing the primary key
        @type _methods: map of 'name':<SchemaMethod> objects
        @param _methods: all methods provided by this schema
        """
        super(SchemaObjectClass, self).__init__(pname, 
                                                cname, 
                                                SchemaTypeData,
                                                desc,
                                                _hash)
        self._properties = _props.copy()
        self._pkeyNames = _pkey[:]
        self._methods = _methods.copy()

    def getPrimaryKeyList(self): return self._pkeyNames[:]

    def getPropertyCount(self): return len(self._properties)
    def getProperties(self): return self._properties.copy()
    def getProperty(self, name): return self._properties[name]

    def getMethodCount(self): return len(self._methods)
    def getMethods(self): return self._methods.copy()
    def getMethod(self, name): return self._methods[name]

    def addProperty(self, name, prop):
        self._properties[name] = prop
        # need to re-generate schema hash
        self._classId = None

    def addMethod(self, name, method): 
        self._methods[name] = method
        self._classId = None



def SchemaObjectClassFactory( param ):
    """
    Factory for constructing a SchemaObjectClass from various sources.

    @type param: various
    @param param: object to use for constructing a SchemaObjectClass instance
    @rtype: SchemaObjectClass
    @returns: a new SchemaObjectClass instance
    """
    if type(param) == dict:
        classId = None
        properties = {}
        methods = {}
        pkey = []
        if "schema_id" in param:
            classId = SchemaClassIdFactory(param["schema_id"])
        if (not classId) or (classId.getType() != SchemaTypeData):
            raise TypeError("Invalid SchemaClassId specified: %s" % classId)
        if "desc" in param:
            desc = param["desc"]
        if "primary_key" in param:
            pkey = param["primary_key"]
        if "properties" in param:
            for name,val in param["properties"].iteritems():
                properties[name] = SchemaPropertyFactory(val)
        if "methods" in param:
            for name,val in param["methods"].iteritems():
                methods[name] = SchemaMethodFactory(val)

        return SchemaObjectClass( classId.getPackageName(),
                                  classId.getClassName(),
                                  desc,
                                  _hash = classId.getHashString(),
                                  _props = properties, _pkey = pkey,
                                  _methods = methods)

    else:
        raise TypeError("Invalid type for SchemaObjectClass construction")



class SchemaEventClass(SchemaClass):
    """
    A schema class that describes an event.  The event is composed
    of zero or more properties.
    
    Map format:
    map["schema_id"] = map, SchemaClassId map for this object.
    map["desc"] = string description of this schema
    map["properties"] = map of "name":SchemaProperty values.
    """
    def __init__( self, pname, cname, desc=None, _props={}, _hash=None ):
        super(SchemaEventClass, self).__init__(pname, 
                                               cname, 
                                               SchemaTypeEvent,
                                               desc,
                                               _hash )
        self._properties = _props.copy()

    def getPropertyCount(self): return len(self._properties)
    def getProperties(self): return self._properties.copy()
    def getProperty(self, name): return self._properties[name]
    def addProperty(self, name, prop):
        self._properties[name] = prop
        # need to re-generate schema hash
        self._classId = None

    def mapEncode(self):
        _map = super(SchemaEventClass, self).mapEncode()
        return _map



def SchemaEventClassFactory( param ):
    """
    Factory for constructing a SchemaEventClass from various sources.

    @type param: various
    @param param: object to use for constructing a SchemaEventClass instance
    @rtype: SchemaEventClass
    @returns: a new SchemaEventClass instance
    """
    if type(param) == dict:
        logging.debug( "constructing SchemaEventClass from map '%s'" % param )
        classId = None
        properties = {}
        if "schema_id" in param:
            classId = SchemaClassIdFactory(param["schema_id"])
        if (not classId) or (classId.getType() != SchemaTypeEvent):
            raise TypeError("Invalid SchemaClassId specified: %s" % classId)
        if "desc" in param:
            desc = param["desc"]
        if "properties" in param:
            for name,val in param["properties"].iteritems():
                properties[name] = SchemaPropertyFactory(val)

        return SchemaEventClass( classId.getPackageName(),
                                  classId.getClassName(),
                                  desc,
                                  _hash = classId.getHashString(),
                                  _props = properties )
    else:
        raise TypeError("Invalid type for SchemaEventClass construction")









