#!/usr/bin/env bash
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

echo "Create a new certificate database for root CA"
rm CA_db/*
certutil -N -d CA_db
                 
echo "Create the self-signed Root CA certificate by entering:"
echo "  password which was specified on creation of root CA database."
echo "  y for 'Is this a CA certificate [y/N]?'"
echo "  [Enter] for 'Enter the path length constraint, enter to skip [<0 for unlimited path]: >'"
echo "  n for 'Is this a critical extension [y/N]?'"
certutil -S -d CA_db -n "MyRootCA" -s "CN=MyRootCA,O=ACME,ST=Ontario,C=CA" -t "CT,," -x -2 -Z SHA1 -v 60
echo "Extract the CA certificate from the CA’s certificate database to a file."
certutil -L -d CA_db -n "MyRootCA" -a -o CA_db/rootca.crt
              

echo "Create a certificate database for the Qpid Broker."
rm server_db/*
certutil -N -d server_db
echo "Import the CA certificate into the broker’s certificate database"
certutil -A -d server_db -n "MyRootCA" -t "TC,," -a -i CA_db/rootca.crt
echo "Create the server certificate request"
certutil -R -d server_db -s "CN=localhost.localdomain,O=ACME,ST=Ontario,C=CA" -a -o server_db/server.req -Z SHA1
echo "Sign and issue a new server certificate by entering:"
echo "  n for 'Is this a CA certificate [y/N]?'"
echo "  '-1' for 'Enter the path length constraint, enter to skip [<0 for unlimited path]: >'"
echo "  n for 'Is this a critical extension [y/N]?'"
echo "  password which was specified on creation of root CA database."
certutil -C -d CA_db -c "MyRootCA" -a -i server_db/server.req -o server_db/server.crt -2 -6  --extKeyUsage serverAuth -v 60 -Z SHA1
echo "Import signed certificate to the broker’s certificate database"
certutil -A -d server_db -n localhost.localdomain -a -i server_db/server.crt -t ",,"
