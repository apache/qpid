##
## Licensed to the Apache Software Foundation (ASF) under one
## or more contributor license agreements.  See the NOTICE file
## distributed with this work for additional information
## regarding copyright ownership.  The ASF licenses this file
## to you under the Apache License, Version 2.0 (the
## "License"); you may not use this file except in compliance
## with the License.  You may obtain a copy of the License at
##
##   http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing,
## software distributed under the License is distributed on an
## "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
## KIND, either express or implied.  See the License for the
## specific language governing permissions and limitations
## under the License
##

import json
from schema   import config_schema
from dispatch import LogAdapter, LOG_TRACE, LOG_ERROR, LOG_INFO

class Section:
  """
  """

  def __init__(self, name, kv_pairs, schema_section):
    self.name           = name
    self.schema_section = schema_section
    self.values         = schema_section.check_and_default(kv_pairs)
    self.index          = schema_section.index_of(kv_pairs)

  def __repr__(self):
    return "%r" % self.values


class ConfigMain:
  """
  """

  def __init__(self, schema):
    self.sections_by_name  = {}
    self.sections_by_index = {}
    self.schema = schema


  def update(self, raw_config):
    for sec_map in raw_config:
      name = sec_map.keys()[0]
      kv   = sec_map[name]
      schema_section = self.schema.sections[name]
      sec = Section(name, kv, schema_section)
      if name not in self.sections_by_name:
        self.sections_by_name[name] = []
      self.sections_by_name[name].append(sec)
      self.sections_by_index[sec.index] = sec
    self._expand_references()


  def item_count(self, name):
    if name in self.sections_by_name:
      return len(self.sections_by_name[name])
    return 0


  def get_value(self, name, idx, key):
    if name in self.sections_by_name:
      sec = self.sections_by_name[name]
      if idx <= len(sec):
        if key in sec[idx].values:
          return sec[idx].values[key]
    return None


  def _expand_references(self):
    for name, sec_list in self.sections_by_name.items():
      for sec in sec_list:
        for k,v in sec.values.items():
          if sec.schema_section.is_expandable(k):
            ref_name = "%s:%s" % (k, v)
            if ref_name in self.sections_by_index:
              ref_section = self.sections_by_index[ref_name]
              for ek,ev in ref_section.values.items():
                if ref_section.schema_section.expand_copy(ek):
                  sec.values[ek] = ev


SECTION_SINGLETON = 0
SECTION_VALUES    = 1

VALUE_TYPE    = 0
VALUE_INDEX   = 1
VALUE_FLAGS   = 2
VALUE_DEFAULT = 3


class SchemaSection:
  """
  """

  def __init__(self, name, section_tuple):
    self.name = name
    self.singleton  = section_tuple[SECTION_SINGLETON]
    self.values     = section_tuple[SECTION_VALUES]
    self.index_keys = []
    finding_index = True
    index_ord     = 0
    while finding_index:
      finding_index = False
      for k,v in self.values.items():
        if v[VALUE_INDEX] == index_ord:
          self.index_keys.append(k)
          index_ord += 1
          finding_index = True


  def is_mandatory(self, key):
    return self.values[key][VALUE_FLAGS].find('M') >= 0


  def is_expandable(self, key):
    return self.values[key][VALUE_FLAGS].find('E') >= 0


  def expand_copy(self, key):
    return self.values[key][VALUE_FLAGS].find('S') >= 0


  def default_value(self, key):
    return self.values[key][VALUE_DEFAULT]


  def check_and_default(self, kv_map):
    copy = {}
    for k,v in self.values.items():
      if k not in kv_map:
        if self.is_mandatory(k):
          raise Exception("In section '%s', missing mandatory key '%s'" % (self.name, k))
        else:
          copy[k] = self.default_value(k)
    for k,v in kv_map.items():
      if k not in self.values:
        raise Exception("In section '%s', unknown key '%s'" % (self.name, k))
      copy[k] = v
    return copy


  def index_of(self, kv_map):
    result = self.name
    for key in self.index_keys:
      result += ':%s' % kv_map[key]
    if result == "":
      result = "SINGLE"
    return result


class Schema:
  """
  """

  def __init__(self):
    self.sections = {}
    for k,v in config_schema.items():
      self.sections[k] = SchemaSection(k, v)



class DXConfig:
  """
  Configuration File Parser for Qpid Dispatch

  Configuration files are made up of "sections" having the following form:

  section-name {
      key0: value0
      key1: value1
      ...
      keyN: valueN
  }

  Sections may be repeated (i.e. there may be multiple instances with the same section name).
  The keys must be unique within a section.  Values can be of string or integer types.  No
  quoting is necessary anywhere in the configuration file.  Values may contain whitespace.

  Comment lines starting with the '#' character will be ignored.

  This parser converts the configuration file into a json string where the file is represented
  as a list of maps.  Each map has one item, the key being the section name and the value being
  a nested map of keys and values from the file.  This json string is parsed into a data
  structure that may then be queried.

  """

  def __init__(self, path):
    self.path = path
    self.raw_config = None
    self.config     = None
    self.schema     = Schema()
    self.log        = LogAdapter('config.parser')


  def read_file(self):
    try:
      self.log.log(LOG_INFO, "Reading Configuration File: %s" % self.path)
      cfile = open(self.path)
      text = cfile.read()
      cfile.close()

      self.json_text = "[" + self._toJson(text) + "]"
      self.raw_config = json.loads(self.json_text);
      self._validate_raw_config()
      self._process_schema()
    except Exception, E:
      print "Exception in read_file: %r" % E
      raise


  def __repr__(self):
    return "%r" % self.config


  def _toJson(self, text):
    lines = text.split('\n')
    stripped = ""
    for line in lines:
      sline = line.strip()

      #
      # Ignore empty lines
      #
      if len(sline) == 0:
        continue

      #
      # Ignore comment lines
      #
      if sline.find('#') == 0:
        continue

      #
      # Convert section opens, closes, and colon-separated key:value lines into json
      #
      if sline[-1:] == '{':
        sline = '{"' + sline[:-1].strip() + '" : {'
      elif sline == '}':
        sline = '}},'
      else:
        colon = sline.find(':')
        if colon > 1:
          sline = '"' + sline[:colon] + '":"' + sline[colon+1:].strip() + '",'
      stripped += sline

      #
      # Remove the trailing commas in map entries
      #
      stripped = stripped.replace(",}", "}")

    #
    # Return the entire document minus the trailing comma
    #
    return stripped[:-1]


  def _validate_raw_config(self):
    """
    Ensure that the configuration is well-formed.  Once this is validated,
    further functions can assume a well-formed data structure is in place.
    """
    if self.raw_config.__class__ != list:
      raise Exception("Invalid Config: Expected List at top level")
    for section in self.raw_config:
      if section.__class__ != dict:
        raise Exception("Invalid Config: List items must be maps")
      if len(section) != 1:
        raise Exception("Invalid Config: Map must have only one entry")
      for key,val in section.items():
        if key.__class__ != str and key.__class__ != unicode:
          raise Exception("Invalid Config: Key in map must be a string")
        if val.__class__ != dict:
          raise Exception("Invalid Config: Value in map must be a map")
        for k,v in val.items():
          if k.__class__ != str and k.__class__ != unicode:
            raise Exception("Invalid Config: Key in section must be a string")
          if v.__class__ != str and v.__class__ != unicode:
            raise Exception("Invalid Config: Value in section must be a string")


  def _process_schema(self):
    self.config = ConfigMain(self.schema)
    self.config.update(self.raw_config)
    self.raw_config = None


  def item_count(self, section):
    """
    Return the number of items in a section (i.e. the number if instances of a section-name).
    """
    result = self.config.item_count(section)
    return result


  def _value(self, section, idx, key):
    return self.config.get_value(section, idx, key)


  def value_string(self, section, idx, key):
    """
    Return the string value for the key in the idx'th item in the section.
    """
    value = self._value(section, idx, key)
    if value:
      return str(value)
    return None


  def value_int(self, section, idx, key):
    """
    Return the integer value for the key in the idx'th item in the section.
    """
    value = self._value(section, idx, key)
    return long(value)


  def value_bool(self, section, idx, key):
    """
    Return the boolean value for the key in the idx'th item in the section.
    """
    value = self._value(section, idx, key)
    if value:
      if str(value) != "no":
        return True
    return None
