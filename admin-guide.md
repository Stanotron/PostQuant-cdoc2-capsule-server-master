# CDOC2 Key Capsule Server Administration Guide

This document describes how to configure and run CDOC2 key capsule servers.

## Database

The key capsule server requires a pre-installed PostgreSQL database to store data.

### Configuration

The creation and updating of the database schema is currently done from the source tree
using `liquibase-maven-plugin`. In order to create or update the database schema
Maven (at least 3.8.4) and Java (at least JDK 17) are required.

In `server-db/liquibase.properties` configure the database connection parameters to match your PostgreSQL
installation:

```
url: jdbc:postgresql://HOST/DB_NAME
username: postgres
password: secret
```

### Create and Update

To create or update the CDOC2 key capsule server's database run the following command from `server-db` folder:

`
mvn liquibase:update
`

## Servers

The CDOC2 Key Capsule Server backend consists of two separate servers:

- CDOC2 Key Capsule Put Server for sending key capsules.
- CDOC2 Key Capsule Get Server for fetching key capsules.

### Keystore Creation

The servers require a keystore file to secure HTTP connections using TLS.

The keystore file is created with the `keytool` utility
(included with Java Runtime).

To generate a keystore file `cdoc2server.p12` with password `passwd`, alias `cdoc2-server` and validity of 3650 days:
```
keytool -genkeypair -alias cdoc2-server -keyalg ec -groupname secp384r1 -sigalg SHA512withECDSA -keystore cdoc2server.p12 -storepass passwd -validity 3650
```

For more details about operations with certificates in keystore files, see [^1].

### Put Server

#### Requirements
- Java runtime (at least JDK 17)
- the application binary `cdoc2-put-server-<VERSION>.jar`
- the configuration file `application.properties`

#### Configuration

The configuration file `application.properties` must contain the following configuration parameters:

```
# The format used for the keystore. It could be set to JKS in case it is a JKS file
server.ssl.key-store-type=PKCS12

# The path to the keystore containing the certificate
server.ssl.key-store=/path/to/cdoc2server.p12

# The keystore password to access its entries
server.ssl.key-store-password=passwd

# The alias mapped to the certificate in the keystore
server.ssl.key-alias=cdoc2-server

# Enable server TLS
server.ssl.enabled=true

# allow only TLSv1.3
server.ssl.enabled-protocols=TLSv1.3

# The port the server is started on
server.port=8443

# Database configuration
spring.datasource.url=jdbc:postgresql://HOST/DB_NAME
spring.datasource.username=postgres
spring.datasource.password=secret
spring.datasource.driver-class-name=org.postgresql.Driver

# logging levels
# change to 'debug' if you want to see logs. Run server with -Dlogging.config=target/test-classes/logback.xml
logging.level.root=info
logging.level.ee.cyber.cdoc2=trace

# Key capsule expiration duration configuration in format P(n)DT(n)H(n)M(n)S
key-capsule.default-expiration-duration=P1095D
# or with specified time (only hours or minutes also can be specified PT1H1M5 or P1DT10H)
key-capsule.default-expiration-duration=P1DT10H30M5S
key-capsule.max-expiration-duration=P1825D
# Expired capsules removing job executes every 5 minutes every day
key-capsule.expired.clean-up.cron=0 0/5 * * * ?
```

#### Running

To run the server, execute the following command:

`
java -jar -Dspring.config.location=application.properties cdoc2-put-server-VER.jar
`

#### Running in Docker

From `1.4.1` release docker images are using `Java 21` as Java base image. To fully use Java21 improvements
enable virtual threads by adding `spring.threads.virtual.enabled=true` into `application.properties`.

To get better throughput it's recommended to give at least 2 CPUs and 1GB of memory per `get-server` and `put-server` instance.
2 GB of memory per process is even better (some additional throughput gains).

Instead of creating two VMs with 1CPU, create single VM with 2 CPU and run both
`get-server` and `put-server` on that VM instance (two Java process per VM sharing 2 CPUs).

For sample setup see:
* [cdoc2-gatling-tests/setup-load-testing](https://github.com/open-eid/cdoc2-gatling-tests/tree/master/setup-load-testing) for `docker run` examples
* [cdoc2-java-ref-impl/test/config/server/docker-compose.yml](https://github.com/open-eid/cdoc2-java-ref-impl/blob/master/test/config/server/docker-compose.yml) for `docker compose` example

### Get Server

#### Requirements
- Java runtime (at least JDK 17)
- the application binary `cdoc2-get-server-<VERSION>.jar`
- the configuration file `application.properties`

#### Truststore Configuration

The CDOC2 Key Capsule Get Server must use client authentication (Mutual TLS) when returning key capsules to clients.

For mutual TLS to work, a trust store file containing trusted certificates is required.

The server truststore file is created using the `keytool` utility.

To add an entry to the trust store file `server-truststore.jks` with password `passwd` execute:

```
keytool -import -trustcacerts -file esteid2018.pem.crt -alias esteid2018 -storepass passwd -keystore server-truststore.jks
```

This will configure the Get Server to trust all requests done by Estonian ID cards signed with the esteid2018 CA certificate.


#### Configuration file

The configuration file `application.properties` must contain the following configuration parameters:

```
#Enable when running on Java21 to improve througput by ~20%
#spring.threads.virtual.enabled=true

# The format used for the keystore. It could be set to JKS in case it is a JKS file
server.ssl.key-store-type=PKCS12

# The path to the keystore containing the certificate
server.ssl.key-store=/path/to/cdoc2server.p12

# The keystore password to access its entries
server.ssl.key-store-password=passwd

# The alias mapped to the certificate in the keystore
server.ssl.key-alias=cdoc2-server

# Enable server TLS
server.ssl.enabled=true
# allow only TLSv1.3
server.ssl.enabled-protocols=TLSv1.3

# The port the server is started on
server.port=8444

# Mutual TLS
server.ssl.client-auth=need
server.ssl.trust-store=/path/to/server-truststore.jks
server.ssl.trust-store-password=passwd
#cdoc2.ssl.client-auth.revocation-checks.enabled=false

# Database configuration
spring.datasource.url=jdbc:postgresql://HOST/DB_NAME
spring.datasource.username=postgres
spring.datasource.password=secret
spring.datasource.driver-class-name=org.postgresql.Driver

# logging levels
# change to 'debug' if you want to see logs. Run server with -Dlogging.config=target/test-classes/logback.xml
logging.level.root=info
logging.level.ee.cyber.cdoc2=trace

# Key capsule expiration duration configuration in format P(n)DT(n)H(n)M(n)S
key-capsule.default-expiration-duration=P1095D
# or with specified time (only hours or minutes also can be specified PT1H1M5 or P1DT10H)
key-capsule.default-expiration-duration=P1DT10H30M5S
key-capsule.max-expiration-duration=P1825D
# Expired capsules removing job executes every 5 minutes every day
key-capsule.expired.clean-up.cron=0 0/5 * * * ?
```

#### Running

To run the server, execute the following command:

`java -jar -Dspring.config.location=application.properties cdoc2-get-server-VER.jar`

####  Client authentication certificate revocation checking
By default, client authentication certificate revocation checking is enabled for get-server.

This option requires connection to external OCSP servers. Est-eID certificates are checked from http://aia.sk.ee/.
Depending on your network and firewall setup, it may be necessary to also configure your firewalls and/or networking proxy servers.

Timeouts in seconds for connection and read timeouts can be specified by setting
`com.sun.security.ocsp.timeout` Java system properties (-D for java executable)

To enable certificate revocation debug use `-Djava.security.debug="certpath"`

Other options available for fine-tuning certificate revocation are described in

https://docs.oracle.com/en/java/javase/17/security/java-pki-programmers-guide.html#GUID-EB250086-0AC1-4D60-AE2A-FC7461374746

To disable client certificate checking over OCSP `application.properties` must have:
```
cdoc2.ssl.client-auth.revocation-checks.enabled=false
```
Or add property `-k` to the curl command to disable SSL certificate check.



## Monitoring

To enable standard Spring monitoring endpoints, `application.properties` must contain following lines:
```
# https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.monitoring
# run management on separate port
management.server.port=18443
management.server.ssl.enabled=true
management.server.ssl.key-store-type=PKCS12
# The path to the keystore containing the certificate
# See copy-keys-and-certificates in pom.xml
management.server.ssl.key-store=../keys/cdoc2server.p12
# The password used to generate the certificate
management.server.ssl.key-store-password=passwd
# The alias mapped to the certificate
management.server.ssl.key-alias=cdoc2-server

# credentials for /actuator/prometheus api basic authentication
management.endpoints.metrics.username=username
management.endpoints.metrics.password=password

# configure monitoring endpoints
management.endpoints.enabled-by-default=false
management.endpoints.web.discovery.enabled=false
# key values can be hidden when change value to "never" or "when_authorized"
management.endpoint.env.show-values=always
management.endpoint.configprops.show-values=always

# explicitly enable endpoints
management.endpoint.info.enabled=true
management.endpoint.health.enabled=true
management.endpoint.health.show-details=always
management.endpoint.startup.enabled=true

# expose endpoints
management.endpoints.web.exposure.include=info,health,startup,prometheus

# Supported metrics
# https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics.supported

#enable tomcat.* metrics
server.tomcat.mbeanregistry.enabled=true

# Spring Data Repository Metrics
# https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics.supported.spring-data-repository
# spring.data.repository.* metrics
management.metrics.data.repository.autotime.enabled=true

# https://docs.spring.io/spring-boot/docs/2.1.5.RELEASE/reference/htmlsingle/#production-ready-metrics-spring-mvc
# http.server.requests metrics
management.metrics.web.server.auto-time-requests=true

# Tracing configuration
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
```

If needed to list all spring boot application.properties enable endpoint `/env` temporary:
```
management.endpoints.web.exposure.include=info,health,startup,prometheus,env
management.endpoint.env.enabled=true
```
Endpoint output can see in `https://<management_host>:<management_port>/actuator/env`


NB! Currently, the monitoring endpoints require no authentication. As these endpoints are
running on a separate HTTP port, the access to the monitoring endpoints must be implemented by network access rules (e.g firewall).
Only `/prometheus` endpoint is authenticated.


### Info endpoint 
`curl -k -X GET https://<management_host>:<management_port>/actuator/info`

```json
{
  "build": {
    "artifact": "cdoc2-put-server",
    "name": "cdoc2-put-server",
    "time": "2023-01-17T14:31:18.918Z",
    "version": "0.3.0-SNAPSHOT",
    "group": "ee.cyber.cdoc2"
  },
  "system.time": "2023-01-17T14:48:39Z"
}
```

### Health endpoint
`curl -k -X GET https://<management_host>:<management_port>/actuator/health`

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499596230656,
        "free": 415045992448,
        "threshold": 10485760,
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### Startup endpoint
`curl -k -X GET https://<management_host>:<management_port>/actuator/startup`

```json
{
  "springBootVersion": "3.2.5",
  "timeline": {
    "startTime": "2023-01-17T14:36:17.935227352Z",
    "events": []
  }
}
```

### Prometheus endpoint (authentication required)
`curl -k -u <username>:<password> https://<management_host>:<management_port>/actuator/prometheus -X GET`

```
# HELP executor_pool_core_threads The core number of threads for the pool
# TYPE executor_pool_core_threads gauge
executor_pool_core_threads{application="cdoc2-put-server",applications="transfer-service",name="applicationTaskExecutor",} 8.0
# HELP jvm_memory_usage_after_gc_percent The percentage of long-lived heap pool used after the last GC event, in the range [0..1]
# TYPE jvm_memory_usage_after_gc_percent gauge
jvm_memory_usage_after_gc_percent{application="cdoc2-put-server",applications="transfer-service",area="heap",pool="long-lived",} 0.00239985994123664
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{application="cdoc2-put-server",applications="transfer-service",area="nonheap",id="Compressed Class Space",} 1.0815688E7
...........
# HELP jdbc_connections_active Current number of active connections that have been allocated from the data source.
# TYPE jdbc_connections_active gauge
...........
```

### Tracing

The CDOC2 Key Capsule Server supports distributed tracing using OpenTelemetry Protocol (OTLP) through Spring Boot's Micrometer integration.
To visualize traces, you need to run a compatible trace backend.

#### Configuration
To enable tracing, add the following configuration parameters to application.properties:
```
# Tracing configuration
# Probability of requests to sample (1.0 = 100%, 0.1 = 10%)
management.tracing.sampling.probability=1.0

# OTLP endpoint for trace export
# Default endpoint for OpenTelemetry Collector or compatible backends
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
```

#### Trace Propagation
The CDOC2 servers support W3C Trace Context propagation, allowing distributed traces to span across multiple services and components.

To propagate traces, include the `traceparent` header in your HTTP requests:
```
traceparent: 00-<trace-id>-<span-id>-01
```
Format breakdown:
* `00` - Version (currently always "00")
* `<trace-id>` - 32-character hexadecimal trace identifier (16 bytes)
* `<span-id>` - 16-character hexadecimal span identifier (8 bytes)
* `01` - Trace flags (01 = sampled, 00 = not sampled)

[^1]: https://docs.oracle.com/cd/E54932_01/doc.705/e54936/cssg_create_ssl_cert.htm#CSVSG182