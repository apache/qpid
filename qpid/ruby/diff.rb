#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require "spec"

spec = Spec.load(File.new($*[0]))

class Symbol
  def <=>(sym)
    to_s <=> sym.to_s
  end
end

def diff(classes)
  sets = classes.map {|c| yield(c).to_set}
  common = sets[0]
  sets[1..-1].each {|s|
    common = common & s
  }

  sep = "\n  "

  puts "Common:\n  #{common.to_a.sort.join(sep)}"
  classes.zip(sets).each {|c, s|
    specific = (s - common).to_a.sort
    puts "\n#{c.name}:\n  #{specific.join(sep)}"
  }
end

classes = $*[1..-1].map {|c|
  spec.classes[c]
}

diff(classes) {|c|
  result = []
  c.methods.each {|m| m.fields.each {|f| result << :"#{m.name}.#{f.name}"}}
  result
}

puts

diff(classes) {|c|
  c.fields.map {|f| f.name}
}
