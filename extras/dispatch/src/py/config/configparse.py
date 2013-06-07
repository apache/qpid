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
    self.config = None

    cfile = open(self.path)
    text = cfile.read()
    cfile.close()

    self.json_text = "[" + self._toJson(text) + "]"
    self.config = json.loads(self.json_text);


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


  def _getSection(self, section):
    result = []
    for item in self.config:
      if item.__class__ == dict and section in item:
        result.append(item[section])
    return result


  def item_count(self, section):
    """
    Return the number of items in a section (i.e. the number if instances of a section-name).
    """
    sec = self._getSection(section)
    return len(sec)

  def _value(self, section, idx, key):
    sec = self._getSection(section)
    if idx >= len(sec):
      return None
    item = sec[idx]
    if item.__class__ == dict and key in item:
      return item[key]
    return None

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


