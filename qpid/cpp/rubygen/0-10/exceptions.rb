#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class GenExceptions < CppGen

  def initialize(outdir, amqp)
    super(outdir, amqp)
    @ns="qpid::amqp_#{@amqp.version.bars}"
    @dir="qpid/amqp_#{@amqp.version.bars}"
  end

  def gen_exceptions()
    h_file("#{@dir}/exceptions") { 
      include "qpid/Exception"
      include "specification.h"
      namespace("#{@ns}") { 
        @amqp.class_("execution").domain("error-code").enum.choices.each { |c|
          name=c.name.typename+"Exception"
          struct(name, "public SessionException") {
            genl "#{name}(const std::string& msg=std::string()) : SessionException(execution::#{c.name.shout}, msg) {}"
          }
        }
      }
    }
  end
  
  def generate()
    gen_exceptions
  end
end

GenExceptions.new($outdir, $amqp).generate();


