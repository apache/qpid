
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


AMQP_QMF_AGENT_LOCATE = "agent.locate"
AMQP_QMF_AGENT_INDICATION = "agent.ind"


AMQP_QMF_SUBJECT = "qmf"
AMQP_QMF_VERSION = 4
AMQP_QMF_SUBJECT_FMT = "%s%d.%s"

class MsgKey(object):
    agent_info = "agent_info"
    query = "query"
    package_info = "package_info"
    schema_id = "schema_id"
    schema = "schema"


class OpCode(object):
    noop = "noop"

    # codes sent by a console and processed by the agent
    agent_locate = "agent-locate"
    cancel_subscription = "cancel-subscription"
    create_subscription = "create-subscription"
    get_query = "get-query"
    method_req = "method"
    renew_subscription = "renew-subscription"
    schema_query = "schema-query"  # @todo: deprecate

    # codes sent by the agent to a console
    agent_ind = "agent"
    data_ind = "data"
    event_ind = "event"
    managed_object = "managed-object"
    object_ind = "object"
    response = "response"
    schema_ind="schema"   # @todo: deprecate




def makeSubject(_code): 
    """
    Create a message subject field value.
    """
    return AMQP_QMF_SUBJECT_FMT % (AMQP_QMF_SUBJECT, AMQP_QMF_VERSION, _code)


def parseSubject(_sub):
    """
    Deconstruct a subject field, return version,opcode values
    """
    if _sub[:3] != "qmf":
        raise Exception("Non-QMF message received")

    return _sub[3:].split('.', 1)


class Notifier(object):
    """
    Virtual base class that defines a call back which alerts the application that
    a QMF Console notification is pending.
    """
    def indication(self):
        """
        Called when one or more items are ready for the application to process.
        This method may be called by an internal QMF library thread.  Its purpose is to
        indicate that the application should process pending work items.
        """
        raise Exception("The indication method must be overridden by the application!")



##==============================================================================
## Addressing
##==============================================================================

class QmfAddress(object):
    """
    TBD
    """
    TYPE_DIRECT = "direct"
    TYPE_TOPIC = "topic"

    ADDRESS_FMT = "qmf.%s.%s/%s"
    DEFAULT_DOMAIN = "default"


    def __init__(self, name, domain, type_):
        self._name = name
        self._domain = domain
        self._type = type_

    def _direct(cls, name, _domain=None):
        if _domain is None:
            _domain = QmfAddress.DEFAULT_DOMAIN
        return cls(name, _domain, type_=QmfAddress.TYPE_DIRECT)
    direct = classmethod(_direct)

    def _topic(cls, name, _domain=None):
        if _domain is None:
            _domain = QmfAddress.DEFAULT_DOMAIN
        return cls(name, _domain, type_=QmfAddress.TYPE_TOPIC)
    topic = classmethod(_topic)


    def get_address(self):
        return str(self)

    def __repr__(self):
        return QmfAddress.ADDRESS_FMT % (self._domain, self._type, self._name)




class AgentName(object):
    """
    Uniquely identifies a management agent within the management domain.
    """
    _separator = ":"

    def __init__(self, vendor, product, name, str_=None):
        """
        Note: this object must be immutable, as it is used to index into a dictionary
        """
        if str_:
            # construct from string representation
            if _str.count(AgentId._separator) < 2:
                raise TypeError("AgentId string format must be 'vendor.product.name'")
            self._vendor, self._product, self._name = param.split(AgentId._separator)
        else:
            self._vendor = vendor
            self._product = product
            self._name = name


    def _from_str(cls, str_):
        return cls(None, None, None, str_=str_)
    from_str = classmethod(_from_str)

    def vendor(self):
        return self._vendor

    def product(self):
        return self._product

    def name(self):
        return self._name

    def __cmp__(self, other):
        if not isinstance(other, AgentName) :
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
        return self._vendor + AgentName._separator + \
            self._product + AgentName._separator + \
            self._name



##==============================================================================
## DATA MODEL
##==============================================================================


class _mapEncoder(object):
    """ 
    virtual base class for all objects that support being converted to a map
    """

    def map_encode(self):
        raise Exception("The map_encode method my be overridden.")


class QmfData(_mapEncoder):
    """
    Base data class representing arbitrarily structure data.  No schema or 
    managing agent is associated with data of this class.

    Map format:
    map["_values"] = map of unordered "name"=<value> pairs (optional)
    map["_subtype"] = map of unordered "name"="subtype string" pairs (optional)
    map["_tag"] = application-specific tag for this instance (optional)
    """
    KEY_VALUES = "_values"
    KEY_SUBTYPES = "_subtypes"
    KEY_TAG="_tag"
    KEY_OBJECT_ID = "_object_id"
    KEY_SCHEMA_ID = "_schema_id"
    KEY_UPDATE_TS = "_update_ts"
    KEY_CREATE_TS = "_create_ts"
    KEY_DELETE_TS = "_delete_ts"

    def __init__(self,
                 _values={}, _subtypes={}, _tag=None, _object_id=None,
                 _ctime = 0, _utime = 0, _dtime = 0,
                 _map=None,
                 _schema=None, _const=False):
        """
        @type _values: dict
        @param _values: dictionary of initial name=value pairs for object's
        named data. 
        @type _subtypes: dict
        @param _subtype: dictionary of subtype strings for each of the object's
        named data. 
        @type _desc: string
        @param _desc: Human-readable description of this data object.
        @type _const: boolean
        @param _const: if true, this object cannot be modified
        """
        self._schema_id = None
        if _map is not None:
            # construct from map
            _tag = _map.get(self.KEY_TAG, _tag)
            _values = _map.get(self.KEY_VALUES, _values)
            _subtypes = _map.get(self.KEY_SUBTYPES, _subtypes)
            _object_id = _map.get(self.KEY_OBJECT_ID, _object_id)
            sid = _map.get(self.KEY_SCHEMA_ID)
            if sid:
                self._schema_id = SchemaClassId(_map=sid)
            _ctime = long(_map.get(self.KEY_CREATE_TS, _ctime))
            _utime = long(_map.get(self.KEY_UPDATE_TS, _utime))
            _dtime = long(_map.get(self.KEY_DELETE_TS, _dtime))

        self._values = _values.copy()
        self._subtypes = _subtypes.copy()
        self._tag = _tag
        self._ctime = _ctime
        self._utime = _utime
        self._dtime = _dtime
        self._const = _const

        if _object_id is not None:
            self._object_id = str(_object_id)
        else:
            self._object_id = None

        if _schema is not None:
            self._set_schema(_schema)
        else:
            # careful: map constructor may have already set self._schema_id, do
            # not override it!
            self._schema = None

    def _create(cls, values, _subtypes={}, _tag=None, _object_id=None,
                _schema=None, _const=False):
        # timestamp in millisec since epoch UTC
        ctime = long(time.time() * 1000)
        return cls(_values=values, _subtypes=_subtypes, _tag=_tag,
                   _ctime=ctime, _utime=ctime,
                   _object_id=_object_id, _schema=_schema, _const=_const)
    create = classmethod(_create)

    def _from_map(cls, map_, _schema=None, _const=False):
        return cls(_map=map_, _schema=_schema, _const=_const)
    from_map = classmethod(_from_map)

    def is_managed(self):
        return self._object_id is not None

    def is_described(self):
        return self._schema_id is not None

    def get_tag(self):
        return self._tag

    def get_value(self, name):
        # meta-properties:
        if name == SchemaClassId.KEY_PACKAGE:
            if self._schema_id:
                return self._schema_id.get_package_name()
            return None
        if name == SchemaClassId.KEY_CLASS:
            if self._schema_id:
                return self._schema_id.get_class_name()
            return None
        if name == SchemaClassId.KEY_TYPE:
            if self._schema_id:
                return self._schema_id.get_type()
            return None
        if name == SchemaClassId.KEY_HASH:
            if self._schema_id:
                return self._schema_id.get_hash_string()
            return None
        if name == self.KEY_SCHEMA_ID:
            return self._schema_id
        if name == self.KEY_OBJECT_ID:
            return self._object_id
        if name == self.KEY_TAG:
            return self._tag
        if name == self.KEY_UPDATE_TS:
            return self._utime
        if name == self.KEY_CREATE_TS:
            return self._ctime
        if name == self.KEY_DELETE_TS:
            return self._dtime

        return self._values.get(name)

    def has_value(self, name):

        if name in [SchemaClassId.KEY_PACKAGE, SchemaClassId.KEY_CLASS,
                    SchemaClassId.KEY_TYPE, SchemaClassId.KEY_HASH,
                    self.KEY_SCHEMA_ID]:
            return self._schema_id is not None
        if name in [self.KEY_UPDATE_TS, self.KEY_CREATE_TS,
                    self.KEY_DELETE_TS]:
            return True
        if name == self.KEY_OBJECT_ID:
            return self._object_id is not None
        if name == self.KEY_TAG:
            return self._tag is not None

        return name in self._values

    def set_value(self, _name, _value, _subType=None):
        if self._const:
            raise Exception("cannot modify constant data object")
        self._values[_name] = _value
        if _subType:
            self._subtypes[_name] = _subType
        return _value

    def get_subtype(self, _name):
        return self._subtypes.get(_name)

    def get_schema_class_id(self): 
        """
        @rtype: class SchemaClassId
        @returns: the identifier of the Schema that describes the structure of the data.
        """
        return self._schema_id

    def get_object_id(self): 
        """
        Get the instance's identification string.
        @rtype: str
        @returns: the identification string, or None if not assigned and id. 
        """
        if self._object_id:
            return self._object_id

        # if object id not assigned, see if schema defines a set of field
        # values to use as an id
        if not self._schema: 
            return None

        ids = self._schema.get_id_names()
        if not ids:
            return None

        if not self._validated:
            self._validate()

        result = u""
        for key in ids:
            try:
                result += unicode(self._values[key])
            except:
                logging.error("get_object_id(): cannot convert value '%s'."
                              % key)
                return None
        self._object_id = result
        return result

    def map_encode(self):
        _map = {}
        if self._tag:
            _map[self.KEY_TAG] = self._tag

        # data in the _values map may require recursive map_encode()
        vmap = {}
        for name,val in self._values.iteritems():
            if isinstance(val, _mapEncoder):
                vmap[name] = val.map_encode()
            else:
                # otherwise, just toss in the native type...
                vmap[name] = val

        _map[self.KEY_VALUES] = vmap
        # subtypes are never complex, so safe to just copy
        _map[self.KEY_SUBTYPES] = self._subtypes.copy()
        if self._object_id:
            _map[self.KEY_OBJECT_ID] = self._object_id
        if self._schema_id:
            _map[self.KEY_SCHEMA_ID] = self._schema_id.map_encode()
        return _map

    def _set_schema(self, schema):
        self._validated = False
        self._schema = schema
        if schema:
            self._schema_id = schema.get_class_id()
            if self._const:
                self._validate()
        else:
            self._schema_id = None

    def _validate(self):
        """
        Compares this object's data against the associated schema.  Throws an 
        exception if the data does not conform to the schema.
        """
        props = self._schema.get_properties()
        for name,val in props.iteritems():
            # @todo validate: type compatible with amqp_type?
            # @todo validate: primary keys have values
            if name not in self._values:
                if val._isOptional:
                    # ok not to be present, put in dummy value
                    # to simplify access
                    self._values[name] = None
                else:
                    raise Exception("Required property '%s' not present." % name)
        self._validated = True

    def __repr__(self):
        return "QmfData=<<" + str(self.map_encode()) + ">>"
        

    def __setattr__(self, _name, _value):
        # ignore private data members
        if _name[0] == '_':
            return super(QmfData, self).__setattr__(_name, _value)
        if _name in self._values:
            return self.set_value(_name, _value)
        return super(QmfData, self).__setattr__(_name, _value)

    def __getattr__(self, _name):
        if _name != "_values" and _name in self._values: 
            return self._values[_name]
        raise AttributeError("no value named '%s' in this object" % _name)

    def __getitem__(self, _name):
        return self.__getattr__(_name)

    def __setitem__(self, _name, _value):
        return self.__setattr__(_name, _value)



class QmfEvent(QmfData):
    """
    A QMF Event is a type of described data that is not managed. Events are 
    notifications that are sent by Agents. An event notifies a Console of a 
    change in some aspect of the system under managment.
    """
    KEY_TIMESTAMP = "_timestamp"

    def __init__(self, _timestamp=None, _values={}, _subtypes={}, _tag=None,
                 _map=None,
                 _schema=None, _const=True):
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

        if _map is not None:
            # construct from map
            super(QmfEvent, self).__init__(_map=_map, _schema=_schema,
                                           _const=_const)
            _timestamp = _map.get(self.KEY_TIMESTAMP, _timestamp)
        else:
            super(QmfEvent, self).__init__(_values=_values,
                                           _subtypes=_subtypes, _tag=_tag,
                                           _schema=_schema, _const=_const)
        if _timestamp is None:
            raise TypeError("QmfEvent: a valid timestamp is required.")

        try:
            self._timestamp = long(_timestamp)
        except:
            raise TypeError("QmfEvent: a numeric timestamp is required.")

    def _create(cls, timestamp, values,
                _subtypes={}, _tag=None, _schema=None, _const=False):
        return cls(_timestamp=timestamp, _values=values, _subtypes=_subtypes,
                _tag=_tag, _schema=_schema, _const=_const)
    create = classmethod(_create)

    def _from_map(cls, map_, _schema=None, _const=False):
        return cls(_map=map_, _schema=_schema, _const=_const)
    from_map = classmethod(_from_map)

    def get_timestamp(self): 
        return self._timestamp

    def map_encode(self):
        _map = super(QmfEvent, self).map_encode()
        _map[self.KEY_TIMESTAMP] = self._timestamp
        return _map





#==============================================================================
#==============================================================================
#==============================================================================




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



# def _doQuery(predicate, params ):
#     """
#     Given the predicate from a query, and a map of named parameters, apply the predicate
#     to the parameters, and return True or False.
#     """
#     if type(predicate) != list or len(predicate) < 1:
#         return False

#     elif opr == Query._LOGIC_AND:
#         logging.debug("_doQuery() AND: [%s]" % predicate )
#         rc = False
#         for exp in predicate[1:]:
#             rc = _doQuery( exp, params )
#             if not rc:
#                 break
#         return rc

#     elif opr == Query._LOGIC_OR:
#         logging.debug("_doQuery() OR: [%s]" % predicate )
#         rc = False
#         for exp in predicate[1:]:
#             rc = _doQuery( exp, params )
#             if rc:
#                 break
#         return rc

#     elif opr == Query._LOGIC_NOT:
#         logging.debug("_doQuery() NOT: [%s]" % predicate )
#         if len(predicate) != 2:
#             logging.warning("Malformed query not-expression received: '%s'" % predicate)
#             return False
#         return not _doQuery( predicate[1:], params )



#     else:
#         logging.warning("Unknown query operator received: '%s'" % opr)
#     return False



class QmfQuery(_mapEncoder):

    _TARGET="what"
    _PREDICATE="where"

    #### Query Targets ####
    _TARGET_PACKAGES="schema_package"
    # (returns just package names)
    # predicate key(s):
    #
    #_PRED_PACKAGE


    _TARGET_SCHEMA_ID="schema_id"
    _TARGET_SCHEMA="schema"
    # predicate key(s):
    #
    #_PRED_PACKAGE
    #_PRED_CLASS
    #_PRED_TYPE
    #_PRED_HASH
    #_PRED_SCHEMA_ID
    # name of property (exist test only)
    # name of method (exist test only)


    _TARGET_AGENT="agent"
    # predicate keys(s):
    #
    #_PRED_VENDOR="_vendor"
    #_PRED_PRODUCT="_product"
    #_PRED_NAME="_name"

    _TARGET_OBJECT_ID="object_id"
    _TARGET_OBJECT="object"
    # package and class names must be suppled in the target value:
    # predicate on all values or meta-values[tbd]
    #
    #_PRED_PACKAGE
    #_PRED_CLASS
    #_PRED_TYPE
    #_PRED_HASH
    #_primary_key
    #_PRED_SCHEMA_ID
    #_PRED_OBJECT_ID
    #_PRED_UPDATE_TS
    #_PRED_CREATE_TS
    #_PRED_DELETE_TS
    #<name of property>

    _PRED_PACKAGE="_package_name"
    _PRED_CLASS="_class_name"
    _PRED_TYPE="_type"
    _PRED_HASH="_hash_str"
    _PRED_VENDOR="_vendor"
    _PRED_PRODUCT="_product"
    _PRED_NAME="_name"
    _PRED_PRIMARY_KEY="_primary_key"
    _PRED_SCHEMA_ID="_schema_id"
    _PRED_OBJECT_ID="_object_id"
    _PRED_UPDATE_TS="_update_ts"
    _PRED_CREATE_TS="_create_ts"
    _PRED_DELETE_TS="_delete_ts"

    _CMP_EQ="eq"
    _CMP_NE="ne"
    _CMP_LT="lt"
    _CMP_LE="le"
    _CMP_GT="gt"
    _CMP_GE="ge"
    _CMP_RE_MATCH="re_match"
    _CMP_EXISTS="exists"
    _CMP_TRUE="true"
    _CMP_FALSE="false"

    _LOGIC_AND="and"
    _LOGIC_OR="or"
    _LOGIC_NOT="not"

    _valid_targets = [_TARGET_PACKAGES, _TARGET_OBJECT_ID, _TARGET_SCHEMA, _TARGET_SCHEMA_ID, 
                      _TARGET_OBJECT, _TARGET_AGENT]

    def __init__(self, qmap):
        """
        """
        self._target_map = None
        self._predicate = None

        if type(qmap) != dict:
            raise TypeError("constructor must be of type dict")

        if self._TARGET in qmap:
            self._target_map = qmap[self._TARGET]
            if self._PREDICATE in qmap:
                self.setPredicate(qmap[self._PREDICATE])
            return
        else:
            # assume qmap to be the target map
            self._target_map = qmap[:]


    def setPredicate(self, predicate):
        """
        """
        if isinstance(predicate, QmfQueryPredicate):
            self._predicate = predicate
        elif type(predicate) == dict:
            self._predicate = QmfQueryPredicate(predicate)
        else:
            raise TypeError("Invalid type for a predicate: %s" % str(predicate))


    def evaluate(self, qmfData):
        """
        """
        # @todo: how to interpred qmfData against target??????
        #
        if self._predicate:
            return self._predicate.evaluate(qmfData)
        # no predicate - always match
        return True

    def getTarget(self):
        for key in self._target_map.iterkeys():
            if key in self._valid_targets:
                return key
        return None

    def getPredicate(self):
        return self._predicate

    def map_encode(self):
        _map = {}
        _map[self._TARGET] = self._target_map
        if self._predicate is not None:
            _map[self._PREDICATE] = self._predicate.map_encode()
        return _map

    def __repr__(self):
        return "QmfQuery=<<" + str(self.map_encode()) + ">>"



class QmfQueryPredicate(_mapEncoder):
    """
    Class for Query predicates.
    """
    _valid_cmp_ops = [QmfQuery._CMP_EQ, QmfQuery._CMP_NE, QmfQuery._CMP_LT, 
                      QmfQuery._CMP_GT, QmfQuery._CMP_LE, QmfQuery._CMP_GE,
                      QmfQuery._CMP_EXISTS, QmfQuery._CMP_RE_MATCH,
                      QmfQuery._CMP_TRUE, QmfQuery._CMP_FALSE]
    _valid_logic_ops = [QmfQuery._LOGIC_AND, QmfQuery._LOGIC_OR, QmfQuery._LOGIC_NOT]


    def __init__( self, pmap):
        """
        {"op": listOf(operands)}
        """
        self._oper = None
        self._operands = []

        logic_op = False
        if type(pmap) == dict:
            for key in pmap.iterkeys():
                if key in self._valid_cmp_ops:
                    # coparison operation - may have "name" and "value"
                    self._oper = key
                    break
                if key in self._valid_logic_ops:
                    logic_op = True
                    self._oper = key
                    break

            if not self._oper:
                raise TypeError("invalid predicate expression: '%s'" % str(pmap))

            if type(pmap[self._oper]) == list or type(pmap[self._oper]) == tuple:
                if logic_op:
                    for exp in pmap[self._oper]:
                        self.append(QmfQueryPredicate(exp))
                else:
                    self._operands = list(pmap[self._oper])

        else:
            raise TypeError("invalid predicate: '%s'" % str(pmap))


    def append(self, operand):
        """
        Append another operand to a predicate expression
        """
        self._operands.append(operand)



    def evaluate( self, qmfData ):
        """
        """
        if not isinstance(qmfData, QmfData):
            raise TypeError("Query expects to evaluate QmfData types.")

        if self._oper == QmfQuery._CMP_TRUE:
            logging.debug("query evaluate TRUE")
            return True
        if self._oper == QmfQuery._CMP_FALSE:
            logging.debug("query evaluate FALSE")
            return False

        if self._oper in [QmfQuery._CMP_EQ, QmfQuery._CMP_NE, QmfQuery._CMP_LT, 
                          QmfQuery._CMP_LE, QmfQuery._CMP_GT, QmfQuery._CMP_GE,
                          QmfQuery._CMP_RE_MATCH]:
            if len(self._operands) != 2:
                logging.warning("Malformed query compare expression received: '%s, %s'" %
                                (self._oper, str(self._operands)))
                return False
            # @todo: support regular expression match
            name = self._operands[0]
            logging.debug("looking for: '%s'" % str(name))
            if not qmfData.has_value(name):
                logging.warning("Malformed query, attribute '%s' not present."
                          % name)
                return False

            arg1 = qmfData.get_value(name)
            arg2 = self._operands[1]
            logging.debug("query evaluate %s: '%s' '%s' '%s'" % 
                          (name, str(arg1), self._oper, str(arg2)))
            try:
                if self._oper == QmfQuery._CMP_EQ: return arg1 == arg2
                if self._oper == QmfQuery._CMP_NE: return arg1 != arg2
                if self._oper == QmfQuery._CMP_LT: return arg1 < arg2
                if self._oper == QmfQuery._CMP_LE: return arg1 <= arg2
                if self._oper == QmfQuery._CMP_GT: return arg1 > arg2
                if self._oper == QmfQuery._CMP_GE: return arg1 >= arg2
                if self._oper == QmfQuery._CMP_RE_MATCH: 
                    logging.error("!!! RE QUERY TBD !!!")
                    return False
            except:
                pass
            logging.warning("Malformed query - %s: '%s' '%s' '%s'" % 
                            (name, str(arg1), self._oper, str(self._operands[1])))
            return False


        if self._oper == QmfQuery._CMP_EXISTS:
            if len(self._operands) != 1:
                logging.warning("Malformed query present expression received")
                return False
            name = self._operands[0]
            logging.debug("query evaluate PRESENT: [%s]" % str(name))
            return qmfData.has_value(name)

        if self._oper == QmfQuery._LOGIC_AND:
            logging.debug("query evaluate AND: '%s'" % str(self._operands))
            for exp in self._operands:
                if not exp.evaluate(qmfData):
                    return False
            return True

        if self._oper == QmfQuery._LOGIC_OR:
            logging.debug("query evaluate OR: [%s]" % str(self._operands))
            for exp in self._operands:
                if exp.evaluate(qmfData):
                    return True
            return False

        if self._oper == QmfQuery._LOGIC_NOT:
            logging.debug("query evaluate NOT: [%s]" % str(self._operands))
            for exp in self._operands:
                if exp.evaluate(qmfData):
                    return False
            return True

        logging.warning("Unrecognized query operator: [%s]" % str(self._oper))
        return False

    
    def map_encode(self):
        _map = {}
        _list = []
        for exp in self._operands:
            if isinstance(exp, QmfQueryPredicate):
                _list.append(exp.map_encode())
            else:
                _list.append(exp)
        _map[self._oper] = _list
        return _map


    def __repr__(self):
        return "QmfQueryPredicate=<<" + str(self.map_encode()) + ">>"
    


##==============================================================================
## SCHEMA
##==============================================================================


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



class SchemaClassId(_mapEncoder):
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
    KEY_PACKAGE="_package_name"
    KEY_CLASS="_class_name"
    KEY_TYPE="_type"
    KEY_HASH="_hash_str"

    TYPE_DATA = "_data"
    TYPE_EVENT = "event"

    _valid_types=[TYPE_DATA, TYPE_EVENT]
    _schemaHashStrFormat = "%08x-%08x-%08x-%08x"
    _schemaHashStrDefault = "00000000-00000000-00000000-00000000"

    def __init__(self, pname=None, cname=None, stype=TYPE_DATA, hstr=None,
                 _map=None): 
        """
        @type pname: str
        @param pname: the name of the class's package
        @type cname: str
        @param cname: name of the class
        @type stype: str
        @param stype: schema type [data | event]
        @type hstr: str
        @param hstr: the hash value in '%08x-%08x-%08x-%08x' format
        """
        if _map is not None:
            # construct from map
            pname = _map.get(self.KEY_PACKAGE, pname)
            cname = _map.get(self.KEY_CLASS, cname)
            stype = _map.get(self.KEY_TYPE, stype)
            hstr = _map.get(self.KEY_HASH, hstr)

        self._pname = pname
        self._cname = cname
        if stype not in SchemaClassId._valid_types:
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
    # constructor
    def _create(cls, pname, cname, stype=TYPE_DATA, hstr=None):
        return cls(pname=pname, cname=cname, stype=stype, hstr=hstr)
    create = classmethod(_create)

    # map constructor 
    def _from_map(cls, map_):
        return cls(_map=map_)
    from_map = classmethod(_from_map)

    def get_package_name(self): 
        """ 
        Access the package name in the SchemaClassId.

        @rtype: str
        """
        return self._pname


    def get_class_name(self): 
        """ 
        Access the class name in the SchemaClassId

        @rtype: str
        """
        return self._cname


    def get_hash_string(self): 
        """ 
        Access the schema's hash as a string value

        @rtype: str
        """
        return self._hstr


    def get_type(self):
        """ 
        Returns the type code associated with this Schema

        @rtype: str
        """
        return self._type

    def map_encode(self):
        _map = {}
        _map[self.KEY_PACKAGE] = self._pname
        _map[self.KEY_CLASS] = self._cname
        _map[self.KEY_TYPE] = self._type
        if self._hstr: _map[self.KEY_HASH] = self._hstr
        return _map

    def __repr__(self):
        hstr = self.get_hash_string()
        if not hstr:
            hstr = SchemaClassId._schemaHashStrDefault
        return self._pname + ":" + self._cname + ":" + self._type +  "(" + hstr  + ")"


    def __cmp__(self, other):
        if isinstance(other, dict):
            other = SchemaClassId.from_map(other)
        if not isinstance(other, SchemaClassId):
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



class SchemaProperty(_mapEncoder):
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
    _dir_strings = ["I", "O", "IO"]
    def __init__(self, _type_code=None, _map=None, kwargs={}):
        if _map is not None:
            # construct from map
            _type_code = _map.get("amqp_type", _type_code)
            kwargs = _map
            if not _type_code:
                raise TypeError("SchemaProperty: amqp_type is a mandatory"
                                " parameter")

        self._type = _type_code
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
        self._dir = None
        self._default = None

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
            elif key == "dir":
                value = str(value).upper()
                if value not in self._dir_strings:
                    raise TypeError("invalid value for direction parameter: '%s'" % value)
                self._dir = value
            elif key == "default" : self._default = value

    # constructor
    def _create(cls, type_code, kwargs={}):
        return cls(_type_code=type_code, kwargs=kwargs)
    create = classmethod(_create)

    # map constructor
    def _from_map(cls, map_):
        return cls(_map=map_)
    from_map = classmethod(_from_map)

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

    def getDirection(self): return self._dir

    def getDefault(self): return self._default

    def map_encode(self):
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
        if self._dir: _map["dir"] = self._dir
        if self._default: _map["default"] = self._default
        return _map

    def __repr__(self): 
        return "SchemaProperty=<<" + str(self.map_encode()) + ">>"

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
        if self._dir: hasher.update(self._dir)
        if self._default: hasher.update(self._default)



class SchemaMethod(_mapEncoder):
    """ 
    The SchemaMethod class describes the method's structure, and contains a
    SchemaProperty class for each argument declared by the method.

    Map format:
    map["arguments"] = map of "name"=<SchemaProperty> pairs.
    map["desc"] = str, description of the method
    """
    def __init__(self, args={}, _desc=None, _map=None):
        """
        Construct a SchemaMethod.

        @type args: map of "name"=<SchemaProperty> objects
        @param args: describes the arguments accepted by the method
        @type _desc: str
        @param _desc: Human-readable description of the schema
        """
        if _map is not None:
            _desc = _map.get("desc")
            margs = _map.get("arguments", args)
            # margs are in map format - covert to SchemaProperty
            args = {}
            for name,val in margs.iteritems():
                args[name] = SchemaProperty.from_map(val)

        self._arguments = args.copy()
        self._desc = _desc

    # map constructor
    def _from_map(cls, map_):
        return cls(_map=map_)
    from_map = classmethod(_from_map)

    def getDesc(self): return self._desc

    def getArgCount(self): return len(self._arguments)

    def getArguments(self): return self._arguments.copy()

    def getArgument(self, name): return self._arguments[name]

    def add_argument(self, name, schema):
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

    def map_encode(self):
        """
        Return the map encoding of this schema.
        """
        _map = {}
        _args = {}
        for name,val in self._arguments.iteritems():
            _args[name] = val.map_encode()
        _map["arguments"] = _args
        if self._desc: _map["desc"] = self._desc
        return _map

    def __repr__(self):
        result = "SchemaMethod=<<args=("
        first = True
        for name,arg in self._arguments.iteritems():
            if first:
                first = False
            else:
                result += ", "
            result += name
        result += ")>>"
        return result

    def _updateHash(self, hasher):
        """
        Update the given hash object with a hash computed over this schema.
        """
        for name,val in self._arguments.iteritems():
            hasher.update(name)
            val._updateHash(hasher)
        if self._desc: hasher.update(self._desc)



class SchemaClass(QmfData):
    """
    Base class for Data and Event Schema classes.

    Map format:
    map(QmfData), plus:
    map["_schema_id"] = map representation of a SchemaClassId instance
    map["_primary_key_names"] = order list of primary key names
    """
    KEY_SCHEMA_ID="_schema_id"
    KEY_PRIMARY_KEY_NAMES="_primary_key_names"
    KEY_DESC = "_desc"

    SUBTYPE_PROPERTY="qmfProperty"
    SUBTYPE_METHOD="qmfMethod"

    def __init__(self, _classId=None, _desc=None, _map=None):
        """
        Schema Class constructor.

        @type classId: class SchemaClassId
        @param classId: Identifier for this SchemaClass
        @type _desc: str
        @param _desc: Human-readable description of the schema
        """
        if _map is not None:
            super(SchemaClass, self).__init__(_map=_map)

            # decode each value based on its type
            for name,value in self._values.iteritems():
                if self._subtypes.get(name) == self.SUBTYPE_METHOD:
                    self._values[name] = SchemaMethod.from_map(value)
                else:
                    self._values[name] = SchemaProperty.from_map(value)
            cid = _map.get(self.KEY_SCHEMA_ID)
            if cid:
                _classId = SchemaClassId.from_map(cid)
            self._object_id_names = _map.get(self.KEY_PRIMARY_KEY_NAMES,[])
            _desc = _map.get(self.KEY_DESC)
        else:
            super(SchemaClass, self).__init__()
            self._object_id_names = []

        self._classId = _classId
        self._desc = _desc

    def get_class_id(self): 
        if not self._classId.get_hash_string():
            self.generate_hash()
        return self._classId

    def get_desc(self): return self._desc

    def generate_hash(self): 
        """
        generate an md5 hash over the body of the schema,
        and return a string representation of the hash
        in format "%08x-%08x-%08x-%08x"
        """
        md5Hash = _md5Obj()
        md5Hash.update(self._classId.get_package_name())
        md5Hash.update(self._classId.get_class_name())
        md5Hash.update(self._classId.get_type())
        for name,x in self._values.iteritems():
            md5Hash.update(name)
            x._updateHash( md5Hash )
        for name,value in self._subtypes.iteritems():
            md5Hash.update(name)
            md5Hash.update(value)
        idx = 0
        for name in self._object_id_names:
            md5Hash.update(str(idx) + name)
            idx += 1
        hstr = md5Hash.hexdigest()[0:8] + "-" +\
            md5Hash.hexdigest()[8:16] + "-" +\
            md5Hash.hexdigest()[16:24] + "-" +\
            md5Hash.hexdigest()[24:32]
        # update classId with new hash value
        self._classId._hstr = hstr
        return hstr


    def get_property_count(self): 
        count = 0
        for value in self._subtypes.itervalues():
            if value == self.SUBTYPE_PROPERTY:
                count += 1
        return count

    def get_properties(self): 
        props = {}
        for name,value in self._subtypes.iteritems():
            if value == self.SUBTYPE_PROPERTY:
                props[name] = self._values.get(name)
        return props

    def get_property(self, name):
        if self._subtypes.get(name) == self.SUBTYPE_PROPERTY:
            return self._values.get(name)
        return None

    def add_property(self, name, prop):
        self.set_value(name, prop, self.SUBTYPE_PROPERTY)
        # need to re-generate schema hash
        self._classId._hstr = None

    def get_value(self, name):
        # check for meta-properties first
        if name == SchemaClassId.KEY_PACKAGE:
            return self._classId.get_package_name()
        if name == SchemaClassId.KEY_CLASS:
            return self._classId.get_class_name()
        if name == SchemaClassId.KEY_TYPE:
            return self._classId.get_type()
        if name == SchemaClassId.KEY_HASH:
            return self.get_class_id().get_hash_string()
        if name == self.KEY_SCHEMA_ID:
            return self.get_class_id()
        if name == self.KEY_PRIMARY_KEY_NAMES:
            return self._object_id_names[:]
        return super(SchemaClass, self).get_value(name)

    def has_value(self, name):
        if name in [SchemaClassId.KEY_PACKAGE, SchemaClassId.KEY_CLASS, SchemaClassId.KEY_TYPE,
                    SchemaClassId.KEY_HASH, self.KEY_SCHEMA_ID, self.KEY_PRIMARY_KEY_NAMES]:
            return True
        super(SchemaClass, self).has_value(name)

    def map_encode(self):
        """
        Return the map encoding of this schema.
        """
        _map = super(SchemaClass,self).map_encode()
        _map[self.KEY_SCHEMA_ID] = self.get_class_id().map_encode()
        if self._object_id_names:
            _map[self.KEY_PRIMARY_KEY_NAMES] = self._object_id_names[:]
        if self._desc:
            _map[self.KEY_DESC] = self._desc
        return _map

    def __repr__(self):
        return str(self.get_class_id())



class SchemaObjectClass(SchemaClass):
    """
    A schema class that describes a data object.  The data object is composed 
    of zero or more properties and methods.  An instance of the SchemaObjectClass
    can be identified using a key generated by concantenating the values of
    all properties named in the primary key list.
    
    Map format:
    map(SchemaClass)
    """
    def __init__(self, _classId=None, _desc=None, 
                 _props={}, _methods={}, _object_id_names=None, 
                 _map=None):
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
        if _map is not None:
            super(SchemaObjectClass,self).__init__(_map=_map)
        else:
            super(SchemaObjectClass, self).__init__(_classId=_classId, _desc=_desc)
            self._object_id_names = _object_id_names
            for name,value in _props.iteritems():
                self.set_value(name, value, self.SUBTYPE_PROPERTY)
            for name,value in _methods.iteritems():
                self.set_value(name, value, self.SUBTYPE_METHOD)

        if self._classId.get_type() != SchemaClassId.TYPE_DATA:
            raise TypeError("Invalid ClassId type for data schema: %s" % self._classId)

    def get_id_names(self): 
        return self._object_id_names[:]

    def get_method_count(self):
        count = 0
        for value in self._subtypes.itervalues():
            if value == self.SUBTYPE_METHOD:
                count += 1
        return count

    def get_methods(self):
        meths = {}
        for name,value in self._subtypes.iteritems():
            if value == self.SUBTYPE_METHOD:
                meths[name] = self._values.get(name)
        return meths

    def get_method(self, name):
        if self._subtypes.get(name) == self.SUBTYPE_METHOD:
            return self._values.get(name)
        return None

    def add_method(self, name, method): 
        self.set_value(name, method, self.SUBTYPE_METHOD)
        # need to re-generate schema hash
        self._classId._hstr = None




class SchemaEventClass(SchemaClass):
    """
    A schema class that describes an event.  The event is composed
    of zero or more properties.
    
    Map format:
    map["schema_id"] = map, SchemaClassId map for this object.
    map["desc"] = string description of this schema
    map["properties"] = map of "name":SchemaProperty values.
    """
    def __init__(self, _classId=None, _desc=None, _props={},
                 _map=None):
        if _map is not None:
            super(SchemaEventClass,self).__init__(_map=_map)
        else:
            super(SchemaEventClass, self).__init__(_classId=_classId,
                                                   _desc=_desc)
            for name,value in _props.iteritems():
                self.set_value(name, value, self.SUBTYPE_PROPERTY)

        if self._classId.get_type() != SchemaClassId.TYPE_EVENT:
            raise TypeError("Invalid ClassId type for event schema: %s" %
                            self._classId)











