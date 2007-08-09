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
  def cppsafe()
    CppMangle.include?(self) ? self+"_" : self
  end
end

# Additional methods for AmqpField.
class AmqpField
  def cppname() @cache_cppname ||= name.lcaps.cppsafe; end
  def cpptype() @cache_cpptype ||= amqp_root.param_type(field_type); end
  def type_name () @type_name ||= cpptype+" "+cppname; end
end

# Additional methods for AmqpMethod
class AmqpMethod
  def cppname() name.lcaps.cppsafe; end
  def param_names() @param_names ||= fields.collect { |f| f.cppname }; end
  def signature() @signature ||= fields.collect { |f| f.cpptype+" "+f.cppname }; end
  def body_name() amqp_parent.name.caps+name.caps+"Body"; end
end

# Additional methods for AmqpClass
class AmqpClass
  def cppname() name.caps; end
  def body_name() cppname+"Body"
  end
end

# Additional methos for AmqpRoot
class AmqpRoot
  # FIXME aconway 2007-06-20: fix u_int types, should be uint
  CppTypeMap={
    "bit"=> ["bool"],
    "octet"=>["u_int8_t"],
    "short"=>["u_int16_t"],
    "long"=>["u_int32_t"],
    "longlong"=>["u_int64_t"],
    "timestamp"=>["u_int64_t"],
    "longstr"=>["string", "const string&"],
    "shortstr"=>["string", "const string&"],
    "table"=>["FieldTable", "const FieldTable&", "const FieldTable&"],
    "content"=>["Content", "const Content&", "const Content&"],
    "rfc1982-long-set"=>["SequenceNumberSet", "const SequenceNumberSet&", "const SequenceNumberSet&"],
    "uuid"=>["string", "const string&"] # FIXME should be: ["Uuid", "const Uuid&", "const Uuid&"]
  }

  def lookup(amqptype)
    CppTypeMap[amqptype] or raise "No cpp type for #{amqptype}";
  end
  
  def member_type(amqptype) lookup(amqptype)[0]; end
  def param_type(amqptype) t=lookup(amqptype); t[1] or t[0]; end
  def return_type(amqptype) t=lookup(amqptype); t[2] or t[0]; end
end

class CppGen < Generator
  def initialize(outdir, *specs)
    super(outdir,*specs)
  end

  # Write a header file. 
  def h_file(path)
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
  def cpp_file(path)
    path = (/\.cpp$/ === path ? path : path+".cpp")
    file(path) do
      gen Copyright
      yield
    end
  end

  def include(header)
    genl /<.*>/.match(header) ? "#include #{header}" : "#include \"#{header}\""
  end

  def scope(open="{",close="}", &block) 
    genl open; indent(&block); genl close
  end

  def namespace(name, &block) 
    genl
    names = name.split("::")
    names.each { |n| genl "namespace #{n} {" }
    genl
    yield
    genl
    genl('}'*names.size+" // "+name)
    genl
  end

  def struct_class(type, name, *bases, &block)
    genl
    gen "#{type} #{name}"
    gen ": #{bases.join(', ')}" unless bases.empty?
    scope(" {","};", &block)
  end

  def struct(name, *bases, &block) struct_class("struct", name, bases, &block); end
  def class_(name, *bases, &block) struct_class("class", name, bases, &block); end

  def typedef(type, name) genl "typedef #{type} #{name};\n" end

  def variant(types) "boost::variant<#{types.join(", ")}>"; end
  def variantl(types) "boost::variant<#{types.join(", \n")}>"; end
  def blank_variant(types) variant(["boost::blank"]+types); end
  def tuple(types) "boost::tuple<#{types.join(', ')}>"; end
  
end

