= Running hello-world.rb =

The ruby client includes a simple hello-world example that publishes
and consumes a message. You can find this in the examples
directory. This example requires a running broker.

You can set RUBYLIB to the directories containing the Qpid ruby
library and the SASL extension, then run the example from the command
line. These are found in the ./lib and ./ext/sasl subdirectories.

$ export RUBYLIB=/home/me/qpid/ruby/lib:/home/me/qpid/ruby/ext/sasl
$ ./hello-world.rb
#<Qpid::Message:0xb761c378 @headers=[#<struct Struct::Qpid_Message_properties content_length=nil, message_id=nil, correlation_id=nil, reply_to=nil, content_type="text/plain", content_encoding=nil, user_id=nil, app_id=nil, application_headers=nil, st_type=message_properties, id=nil>, #<struct Struct::Qpid_Delivery_properties discard_unroutable=nil, immediate=nil, redelivered=nil, priority=nil, delivery_mode=nil, ttl=nil, timestamp=nil, expiration=nil, exchange="", routing_key="test-queue", resume_id=nil, resume_ttl=nil, st_type=delivery_properties, id=nil>], @body="Hello World!", @id=#<Qpid::Serial:0xb76450fc @value=0>>

Alternatively, you can specify the library paths using $ ruby -I:

$ ruby -I /home/me/qpid/ruby/lib:/home/me/qpid/ruby/ext/sasl hello-world.rb 
#<Qpid::Message:0xb7504a44 @headers=[#<struct Struct::Qpid_Message_properties content_length=nil, message_id=nil, correlation_id=nil, reply_to=nil, content_type="text/plain", content_encoding=nil, user_id=nil, app_id=nil, application_headers=nil, st_type=message_properties, id=nil>, #<struct Struct::Qpid_Delivery_properties discard_unroutable=nil, immediate=nil, redelivered=nil, priority=nil, delivery_mode=nil, ttl=nil, timestamp=nil, expiration=nil, exchange="", routing_key="test-queue", resume_id=nil, resume_ttl=nil, st_type=delivery_properties, id=nil>], @body="Hello World!", @id=#<Qpid::Serial:0xb752d548 @value=0>>

= Running the Tests =

The "tests" directory contains a collection of unit tests for the ruby
client. These can be run from the 'ruby' directory with the Rakefile
provided:

$ rake test
