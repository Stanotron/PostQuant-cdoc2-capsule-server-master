#!/bin/bash

# Clear old alias from keystore before generating new key pair
keytool -delete -noprompt -alias cdoc2-server -keystore cdoc2server.p12 -storepass passwd
# Generate server key store
keytool -genkeypair -alias cdoc2-server -keyalg ec -groupname secp384r1 -sigalg SHA512withECDSA \
    -keystore cdoc2server.p12 -storepass passwd -validity 3650 -ext san=ip:127.0.0.1,dns:localhost \
    -dname "cn=cdoc2-server, ou=Unknown, o=Cybernetica AS, c=EE"

keytool -exportcert -keystore cdoc2server.p12 -alias cdoc2-server -storepass passwd -rfc -file server-certificate.pem
openssl x509 -in server-certificate.pem -text


# Clear old certificate from trust store before importing update certificate there
keytool -delete -noprompt -alias cdoc2-server -keystore clienttruststore.jks -storepass passwd
# Add the server certificate to the client's trust store:
keytool -import -noprompt -trustcacerts -file server-certificate.pem -alias cdoc2-server \
    -keypass password -storepass passwd -keystore clienttruststore.jks


# Clear old alias from keystore before generating new key pair
keytool -delete -noprompt -alias cdoc2-client -keystore cdoc2client.p12 -storepass passwd
# Gen temp client key store and add it to server trust store(to be replaced with cert from id-kaart)
keytool -genkeypair -alias cdoc2-client -keyalg ec -groupname secp384r1 -sigalg SHA512withECDSA \
    -keystore cdoc2client.p12 -storepass passwd -validity 3650 -ext san=ip:127.0.0.1,dns:localhost \
    -dname "cn=cdoc2-client, ou=Unknown, o=Cybernetica AS, c=EE"
keytool -exportcert -keystore cdoc2client.p12 -alias cdoc2-client -storepass passwd -rfc -file client-certificate.pem

# Clear old certificate from trust store before importing update certificate there
keytool -delete -noprompt -alias cdoc2-client -keystore servertruststore.jks -storepass passwd
keytool -import -noprompt -trustcacerts -file client-certificate.pem -alias cdoc2-client \
    -storepass passwd -keystore servertruststore.jks

# Move new certificate to ca_certs directory
mv client-certificate.pem ca_certs