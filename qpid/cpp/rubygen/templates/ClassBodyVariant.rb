#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class ClassBodyVariant < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
  end

  def class_body(c)
    h_file (c.body_name) { 
      c.methods.each { |m| genl "#include \"#{m.body_name}.h\""; }
      genl
      genl "#include <boost/visitor.hpp>"
      genl
      gen "typedef boost::variant<"
      indent { genl c.methods().collect { |m| m.body_name }.join(",\n") }
      genl ">  #{c.body_name};"
    }
  end

  def generate()
    @amqp.classes.each{ |c| class_body c }
  end
end

ClassBodyVariant.new(Outdir, Amqp).generate();

