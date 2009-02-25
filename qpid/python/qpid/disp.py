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

from time import strftime, gmtime

class Display:
  """ Display formatting for QPID Management CLI """
  
  def __init__ (self, spacing=2, prefix="    "):
    self.tableSpacing    = spacing
    self.tablePrefix     = prefix
    self.timestampFormat = "%X"

  def table (self, title, heads, rows):
    """ Print a formatted table with autosized columns """

    # Pad the rows to the number of heads
    for row in rows:
      diff = len(heads) - len(row)
      for idx in range(diff):
        row.append("")

    print title
    if len (rows) == 0:
      return
    colWidth = []
    col      = 0
    line     = self.tablePrefix
    for head in heads:
      width = len (head)
      for row in rows:
        cellWidth = len (unicode (row[col]))
        if cellWidth > width:
          width = cellWidth
      colWidth.append (width + self.tableSpacing)
      line = line + head
      if col < len (heads) - 1:
        for i in range (colWidth[col] - len (head)):
          line = line + " "
      col = col + 1
    print line
    line = self.tablePrefix
    for width in colWidth:
      for i in range (width):
        line = line + "="
    print line

    for row in rows:
      line = self.tablePrefix
      col  = 0
      for width in colWidth:
        line = line + unicode (row[col])
        if col < len (heads) - 1:
          for i in range (width - len (unicode (row[col]))):
            line = line + " "
        col = col + 1
      print line

  def do_setTimeFormat (self, fmt):
    """ Select timestamp format """
    if fmt == "long":
      self.timestampFormat = "%c"
    elif fmt == "short":
      self.timestampFormat = "%X"

  def timestamp (self, nsec):
    """ Format a nanosecond-since-the-epoch timestamp for printing """
    return strftime (self.timestampFormat, gmtime (nsec / 1000000000))

  def duration(self, nsec):
    if nsec < 0: nsec = 0
    sec = nsec / 1000000000
    min = sec / 60
    hour = min / 60
    day = hour / 24
    result = ""
    if day > 0:
      result = "%dd " % day
    if hour > 0 or result != "":
      result += "%dh " % (hour % 24)
    if min > 0 or result != "":
      result += "%dm " % (min % 60)
    result += "%ds" % (sec % 60)
    return result
