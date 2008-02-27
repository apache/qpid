#!/usr/bin/ruby
#
# General purpose C++ code generation.
#
require 'amqpgen'
require 'set'

Copyright=<<EOS
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

///
/// This file was automatically generated from the AMQP specification.
/// Do not edit.
///


EOS

CppKeywords = Set.new(["and", "and_eq", "asm", "auto", "bitand",
                       "bitor", "bool", "break", "case", "catch", "char",
                       "class", "compl", "const", "const_cast", "continue",
                       "default", "delete", "do", "DomainInfo", "double",
                       "dynamic_cast", "else", "enum", "explicit", "extern",
                       "false", "float", "for", "friend", "goto", "if",
                       "inline", "int", "long", "mutable", "namespace", "new",
                       "not", "not_eq", "operator", "or", "or_eq", "private",
                       "protected", "public", "register", "reinterpret_cast",
                       "return", "short", "signed", "sizeof", "static",
                       "static_cast", "struct", "switch", "template", "this",
                       "throw", "true", "try", "typedef", "typeid",
                       "typename", "union", "unsigned", "using", "virtual",
                       "void", "volatile", "wchar_t", "while", "xor",
                       "xor_eq"])
# Names that need a trailing "_" to avoid clashes.
CppMangle = CppKeywords+Set.new(["string"])

class String
  def cppsafe() CppMangle.include?(self) ? self+"_" : self; end

  def amqp2cpp()
    path=split(".")
    name=path.pop
    return name.typename if path.empty?
    path.map! { |n| n.nsname }
    return "amqp_0_10::"+(path << name.caps).join("::")
  end

  alias :typename :caps
  alias :nsname :bars
  alias :constname :shout
  alias :funcname :lcaps
  alias :varname :lcaps
end

# Hold information about a C++ type.
# 
# preview - new mapping does not use CppType,
# Each amqp type corresponds exactly by dotted name
# to a type, domain or struct, which in turns
# corresponds by name to a C++ type or typedef.
# (see String.amqp2cpp)
# 
class CppType
  def initialize(name) @name=@param=@ret=name; end
  attr_reader :name, :param, :ret, :code
  
  def retref() @ret="#{name}&"; self; end
  def retcref() @ret="const #{name}&"; self; end
  def passcref() @param="const #{name}&"; self; end
  def code(str) @code=str; self; end
  def defval(str) @defval=str; self; end

  def encode(value, buffer)
    @code ? "#{buffer}.put#{@code}(#{value});" : "#{value}.encode(#{buffer});"
  end

  def decode(value,buffer)
    if @code
      if /&$/===param then
        "#{buffer}.get#{@code}(#{value});"
      else
        "#{value} = #{buffer}.get#{@code}();"
      end
    else
      "#{value}.decode(#{buffer});"
    end
  end

  def default_value()
    return @defval ||= "#{name}()"
  end

  def to_s() name; end;
end

class AmqpElement
  def cppfqname()
    names=parent.dotted_name.split(".")
    # Field children are moved up to method in C++b
    prefix.pop if parent.is_a? AmqpField
    prefix.push cppname
    prefix.join("::")
  end
end
  
class AmqpField
  def cppname() name.lcaps.cppsafe; end
  def cpptype() domain.cpptype;  end
  def bit?() domain.type_ == "bit"; end
  def signature() cpptype.param+" "+cppname; end
  def paramtype() "call_traits<#{type_.amqp2cpp}>::param_type"; end
end

class AmqpMethod
  def cppname() name.lcaps.cppsafe; end
  def param_names() fields.map { |f| f.cppname }; end
  def signature() fields.map { |f| f.signature }; end
  def body_name() parent.name.caps+name.caps+"Body"; end
end

class AmqpAction
  def nsname() name.namespace; end
  def classname() name.typename; end
  def funcname() parent.name.funcname + name.caps; end

  def parameters()
    fields.map { |f| "#{f.paramtype} #{f.cppname}_"}.join(",\n")
  end

  def arguments()
    fields.map { |f| "#{f.cppname}_"}.join(",\n")
  end

  def values()
    fields.map { |f| "#{f.cppname}"}.join(",\n")
  end

  def initializers()
    fields.map { |f| "#{f.cppname}(#{f.cppname}_)}"}.join(",\n")
  end

end

class AmqpCommand
  def base() "Command";  end
end

class AmqpControl
  def base() "Control";  end
end

class AmqpClass
  def cppname() name.caps; end
  def nsname() name.nsname; end
end

class AmqpDomain
  @@typemap = {
    "bit"=> CppType.new("bool").code("Octet").defval("false"),
    "octet"=>CppType.new("uint8_t").code("Octet").defval("0"), 
    "short"=>CppType.new("uint16_t").code("Short").defval("0"),
    "long"=>CppType.new("uint32_t").code("Long").defval("0"),
    "longlong"=>CppType.new("uint64_t").code("LongLong").defval("0"),
    "timestamp"=>CppType.new("uint64_t").code("LongLong").defval("0"),
    "longstr"=>CppType.new("string").passcref.retcref.code("LongString"),
    "shortstr"=>CppType.new("string").passcref.retcref.code("ShortString"),
    "table"=>CppType.new("FieldTable").passcref.retcref,
    "array"=>CppType.new("Array").passcref.retcref,
    "content"=>CppType.new("Content").passcref.retcref,
    "rfc1982-long-set"=>CppType.new("SequenceNumberSet").passcref.retcref,
    "long-struct"=>CppType.new("string").passcref.retcref.code("LongString"),
    "uuid"=>CppType.new("Uuid").passcref.retcref
  }

  def cppname() name.caps; end

  def cpptype()
    d=unalias
    @cpptype ||= @@typemap[d.type_] or
      CppType.new(d.cppname).passcref.retcref or
      raise "Invalid type #{self}"
  end

  def AmqpDomain.lookup_type(t)
    @@typemap[t]
  end
end

class AmqpResult
  def cpptype()
    @cpptype=CppType.new(parent.parent.name.caps+parent.name.caps+"Result").passcref
  end
end

class AmqpStruct
  def cpp_pack_type() AmqpDomain.lookup_type(pack()) or CppType.new("uint16_t"); end
  def cpptype() parent.cpptype; end # preview
  def cppname() cpptype.name;  end # preview
  def classname() name.typename; end
end

class CppGen < Generator
  def initialize(outdir, *specs)
    super(outdir,*specs)
    # need to sort classes for dependencies
    @actions=[]                 # Stack of end-scope actions
  end

  # Write a header file. 
  def h_file(path, &block)
    path = (/\.h$/ === path ? path : path+".h")
    guard=path.upcase.tr('./-','_')
    file(path) { 
      gen "#ifndef #{guard}\n"
      gen "#define #{guard}\n"
      gen Copyright
      yield
      gen "#endif  /*!#{guard}*/\n" 
    }
  end

  # Write a .cpp file.
  def cpp_file(path, &block)
    path = (/\.cpp$/ === path ? path : path+".cpp")
    file(path) do
      gen Copyright
      yield
    end
  end

  def include(header)
    header+=".h" unless /(\.h|[">])$/===header
    header="\"#{header}\"" unless /(^<.*>$)|(^".*"$)/===header
    genl "#include #{header}"
  end

  def scope(open="{",close="}", &block)
    genl open
    indent &block
    genl close
  end
  
  def namespace(name, &block) 
    genl
    names = name.split("::")
    names.each { |n| genl "namespace #{n} {" }
    genl
    yield
    genl
    genl('}'*names.size+" // namespace "+name)
    genl
  end

  def struct_class(type, name, bases, &block)
    gen "#{type} #{name}"
    if (!bases.empty?)
      genl ":"
      indent { gen "#{bases.join(",\n")}" }
    end
    genl
    scope("{","};", &block)
  end

  def struct(name, *bases, &block)
    struct_class("struct", name, bases, &block);
  end
  def cpp_class(name, *bases, &block)
    struct_class("class", name, bases, &block);
  end

  def typedef(type, name) genl "typedef #{type} #{name};\n"; end

  def variant(types) "boost::variant<#{types.join(", ")}>"; end
  def variantl(types) "boost::variant<#{types.join(", \n")}>"; end
  def blank_variant(types) variant(["boost::blank"]+types); end
  def tuple(types) "boost::tuple<#{types.join(', ')}>"; end

  def public() outdent { genl "public:" } end
  def private() outdent { genl "private:" } end
  def protected() outdent { genl "protected:" } end

  # Returns [namespace, classname, filename]
  def parse_classname(full_cname)
    names=full_cname.split("::")
    return names[0..-2].join('::'), names[-1], names.join("/") 
  end

  def doxygen_comment(&block)
    genl "/**"
    prefix(" * ",&block)
    genl " */"
  end

  # Generate code in namespace for each class
  def each_class_ns()
    @amqp.classes.each { |c| namespace(c.nsname) { yield c } }
  end
end

# Fully-qualified class name
class FqClass < Struct.new(:fqname,:namespace,:name,:file)
  def initialize(fqclass)
    names=fqclass.split "::"
    super(fqclass, names[0..-2].join('::'), names[-1], names.join("/"))
  end
end

