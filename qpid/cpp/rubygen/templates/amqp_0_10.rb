#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class Amqp_0_10 < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @namespace="qpid::amqp_0_10"
    @filename="qpid/amqp_0_10"
  end

  def action_h(a, base)
    struct(a.cppname, "public #{base}") {
      genl "static const uint8_t CODE=#{a.code};"
      a.fields.each { |f|
        genl "#{(f.type_.amqp2cpp)} #{f.cppname};"
      }
    }
  end

  def action_cpp(a, base)
    
  end

  def gen_amqp_0_10_h
    h_file("#{@filename}.h") { 
      namespace(@namespace) {
        @amqp.classes.each { |cl|
          namespace(cl.cppname) { 
            struct("ClassInfo") {
              genl "static const uint8_t CODE=#{cl.code};"
              genl "static const char* NAME;"
            }
            cl.commands.each { |c| action_h c, "Command"}
            cl.controls.each { |c| action_h c, "Control"}
          }
        }
      }
    }
  end

  def gen_amqp_0_10_cpp
    cpp_file("#{@filename}.cpp") { 
      include @filename

      namespace(@namespace) {
        @amqp.classes.each { |cl|
          namespace(cl.cppname) {
            genl "static const char* ClassInfo::NAME=\"#{cl.name};"
          }
        }
      }
    }
  end

  def generate
    gen_amqp_0_10_h
    gen_amqp_0_10_cpp
  end
end

Amqp_0_10.new(Outdir, Amqp).generate();

