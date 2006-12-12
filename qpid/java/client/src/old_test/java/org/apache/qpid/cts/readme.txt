The files present in the bin, config and src directories should be copied over a complete copy of jms-cts-0.5-b2.

The path entries on the config/providers.xml and src/compile.sh files should be changed before attempting to run.

The scripts expect a properly configured IBASE environment. Before attempting to run, the amqp provider classes must be packaged and installed. The src/compile.sh script will help to achieve that.