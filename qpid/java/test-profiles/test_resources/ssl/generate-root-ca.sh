echo "Create a new certificate database for root CA"
rm CA_db/*
certutil -N -d CA_db
                 
echo "Create the self-signed Root CA certificate"
echo "Enter the password you specified when creating the root CA database."
echo "y for 'Is this a CA certificate [y/N]?'”
echo "Press enter for 'Enter the path length constraint, enter to skip [<0 for unlimited path]: >'"
echo "n for 'Is this a critical extension [y/N]?'”
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
echo "Sign and issue a new server certificate"
echo "n for 'Is this a CA certificate [y/N]?'"
echo "-1 for 'Enter the path length constraint, enter to skip [<0 for unlimited path]: >'"
echo "n' for 'Is this a critical extension [y/N]?'"
echo "enter the password you specified when creating the root CA database."
certutil -C -d CA_db -c "MyRootCA" -a -i server_db/server.req -o server_db/server.crt -2 -6  --extKeyUsage serverAuth -v 60 -Z SHA1
echo "Import signed certificate to the broker’s certificate database"
certutil -A -d server_db -n localhost.localdomain -a -i server_db/server.crt -t ",,"
