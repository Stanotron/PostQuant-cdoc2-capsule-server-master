
### Generate server key store

Run script `generate_certificates.sh` for generating certificates:
`sh generate_certificates.sh`

Or follow manual instruction to update certificates. 
```
keytool -delete -noprompt -alias cdoc2-server -keystore cdoc2server.p12 -storepass passwd
keytool -genkeypair -alias cdoc2-server -keyalg ec -groupname secp384r1 -sigalg SHA512withECDSA \
    -keystore cdoc2server.p12 -storepass passwd -validity 3650 -ext san=ip:127.0.0.1,dns:localhost \
    -dname "cn=cdoc2-server, ou=Unknown, o=Cybernetica AS, c=EE"
keytool -exportcert -keystore cdoc2server.p12 -alias cdoc2-server -storepass passwd -rfc -file server-certificate.pem
openssl x509 -in server-certificate.pem -text
```

Add the server certificate to the client's trust store:
```
keytool -delete -noprompt -alias cdoc2-server -keystore clienttruststore.jks -storepass passwd
keytool -import -noprompt -trustcacerts -file server-certificate.pem -alias cdoc2-server \
    -keypass password -storepass passwd -keystore clienttruststore.jks
```

Gen temp client key store and add it to server trust store(to be replaced with cert from id-kaart)
```
keytool -delete -noprompt -alias cdoc2-client -keystore cdoc2client.p12 -storepass passwd
keytool -genkeypair -alias cdoc2-client -keyalg ec -groupname secp384r1 -sigalg SHA512withECDSA \
    -keystore cdoc2client.p12 -storepass passwd -validity 3650 -ext san=ip:127.0.0.1,dns:localhost \
    -dname "cn=cdoc2-client, ou=Unknown, o=Cybernetica AS, c=EE"
keytool -exportcert -keystore cdoc2client.p12 -alias cdoc2-client -storepass passwd -rfc -file client-certificate.pem

keytool -delete -noprompt -alias cdoc2-client -keystore servertruststore.jks -storepass passwd
keytool -import -noprompt -trustcacerts -file client-certificate.pem -alias cdoc2-client \
    -storepass passwd -keystore servertruststore.jks
```

Move new `client-certificate.pem` into `ca_certs` directory:
`mv client-certificate.pem ca_certs`

Note: `cdoc2client.p12`and `client-certificate.pem` are used by `cdoc2-cli`. See `cdoc2-cli/config` and `test/bats` (in `cdoc2-java-ref-impl` repository) 

Add TEST of ESTEID-SK 2015, TEST of ESTEID2018 (test id-kaart issuers)
and esteid2018 (id-kaart issuer) and server trust store so that id-kaart certificates are trusted by the server
```
keytool -import -trustcacerts -file ca_certs/TEST_of_ESTEID-SK_2015.pem.crt -alias TEST_of_ESTEID-SK_2015 -storepass passwd -keystore servertruststore.jks
keytool -import -trustcacerts -file ca_certs/esteid2018.pem.crt -alias esteid2018 -storepass passwd -keystore servertruststore.jks
keytool -import -trustcacerts -file ca_certs/TEST_of_ESTEID2018.pem.crt -keystore servertruststore.jks -alias TEST_of_ESTEID2018 -storepass passwd
```

### Update client keys in cdoc2-java-ref-impl repository and gatling tests
Run script in cdoc2-java-ref-impl/cdoc-cli/keys/README.md for updating `client-certificate.pem` and 
keystore `cdoc2client.p12` and for extracting private&public keys.

Run script in cdoc2-gatling_tests/README.md repository for updating keystore `cdoc2client.p12`.
