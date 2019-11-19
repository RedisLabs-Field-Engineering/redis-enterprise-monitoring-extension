#!/bin/bash
sleep 30
redisstatus=$(curl -w "%{http_code}\n" http://localhost:8086/v1/bootstrap -o /dev/null)
echo Redis Enterprise returned $redisstatus
if [ $redisstatus -eq 200 ]
then
    echo Starting to initialize Redis Enterprise
    ipaddr=$(curl http://localhost:8086/v1/bootstrap |\jq '.local_node_info.available_addresses[0].address')
    ipaddr1=$(echo $ipaddr | tr -d '"')
    echo $ipaddr1
    # creating a cluster and node, set cluster credentials
    curl -X POST -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d '{"action":"create_cluster","cluster":{"nodes":[],"name":"cluster.local","clobber":true},"node":{"paths":{"persistent_path":"/var/opt/redislabs/persist","ephemeral_path":"/var/opt/redislabs/tmp"},"identity":{"addr":"'"$ipaddr1"'"}},"license":"","credentials":{"username":"extensions@appdynamics.com","password":"123456"}}' http://localhost:8086/v1/bootstrap/create_cluster
    sleep 20
    # check if instance is up
    code=$(curl -w "%{http_code}\n" -u "extensions@appdynamics.com:123456" http://localhost:8086/v1/cluster -o /dev/null)
    echo $code
    if [ $code -eq  200 ]
    then
      # adding ssl keys
      curl -k -X PUT -u "extensions@appdynamics.com:123456" -H "Content-Type: application/json" -d '{ "name": "api", "key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCnNP5LPggAgXF8\n6O57X1EV71GUsbqto5dxhIXVC1XZ9vnJpk25aFflb0yd1pYjna4nuVvosASou0SK\nSxieGIqXEY6eeQQyJPk8zr1RHFTMcdmLB3a4301OFKWZDFnG+MoZSWhRQVxxsJ3H\nceCnbLxDu5wfI9qYnY4kpM2NZVUDO45W6f9p7BMt/F/i7sc/odoCq0+gsZpDrr3+\ncz5TiMbAgLoH9LrqR6hGr87D7p4cMSZKn7oc3bZ9HIVBSXlPPSNmuvOCMqRSqkH1\nE+slQP0NLJB15u2J8RGGKreFl9A22VV+NEyZS1GQnXoarLN3iP7mp0/amVXpYZ/9\nTjzKtrCbAgMBAAECggEAJguo0PYMXBEYAZP+r5PAn30U0wCduWS/0NSWnyM1JaNM\nstVkWguj9FCe3ks1XmVCe0dx4kAqzznNHqp3r4FmB3m3OdfPXJmNHIjuCsanhN1i\n4n+QRACLVnjcNTbaNqvlUFujoNl+b2AAhqEivPXA4KiPnYZK9u7iqPPW0ZV9An7s\ncVIHEKdnGXK5HfbQs2+GXY1rKQ3UjUdjFd9QdFtXjufrISvlAbcqgG/P6GOMQqLg\nE022SSL5seEK91imVpwTivmtM2naZddGj5alSqPIx8HMvccbu/anXocZAIMGQOxA\nnnJjS+StA3C3lAynVUIHnzl3D+u6qCwtJlVsSp8SAQKBgQDVgNYPeO1sd5cX8cO4\nkwnw0p/JRkOAj08uzxvklnSmHvnqn5RDlY+XrAsqVAirEqTke4cUwAEFY4H6lTuf\nsd0+5rDRoYSZVSdzYxhNj3nML5fkg3tQGT3Ken3tQhkS3W987IoH8CtbbMnHQfkY\neHOK+fR4LHUwr711pVZWVDIWpwKBgQDIfRvCTH0lDMAnyv01gKQaZ71d3UwU2Cax\n12csWoqVd8jV2AzboSUtUJPhjK4cIgJSdVCEAlWAZg317V2L5L/bCkAaguS9z5xp\n65NtxN3uMf/+W45V+J/+Kr1qi4dpx4QwSEZBZN4pFzsQJ7x5c++ZzwPDvgZMb+kE\nq20kihmI7QKBgB1q4RPEr8IQQEtWTodyCx6ZhtauzcI6/MgQVWGLsYrqHblMP5uI\nUf6t/+PXLFKWAQ5STux3AC0D5vbl9Q8t7LQsCRT5UlHmP8dQUhrtt/SAYkdkLmt8\n7tylQBCe2OGWjTQDS4mIeBDiznKcDnxmrFTr19lvFr6cmFhLbbf7ZeNlAoGBAJON\niCWKt7vR04Y13f9Hav9IBFWVrg3VOz0949//zujMXNINjQEDa2IbYIrqR6XK3dyk\nHJrbQOQbuACcLnHr68ugkqcwWHrd1icaSsnJzvAkelxQM8RQFCgyem90uzd9sGr8\ndkBgpSSmxUBRjmPwkJGpiwK/0tDkuCFIrS3sOXjNAoGAC/udK5snvUvizxmaGcaM\nD6bDvuwhu8v91fpkVoKgmISaCCQQH73eYnmKFpFPfRus/MG9EJMKaadScg6mBktb\ntDYiKKH3DdzZyqFpgaNu/0rTttDvHqb4UHa0crccg0htopMBVRnRp4K61XkfOKa7\nRuZLDOUKJZBDMj2Xo+9DUuA=\n-----END PRIVATE KEY-----\n", "certificate": "-----BEGIN CERTIFICATE-----\nMIIDMDCCAhgCAxAAATANBgkqhkiG9w0BAQ0FADBmMQswCQYDVQQGEwJVUzERMA8G\nA1UECBMIQ29sb3JhZG8xEDAOBgNVBAcTB0JvdWxkZXIxEjAQBgNVBAoTCVNuYXBM\nb2dpYzERMA8GA1UECxMIU25hcFRlYW0xCzAJBgNVBAMUAi4qMB4XDTE5MTExOTAx\nMjkyOVoXDTIwMTExODAxMjkyOVowVDELMAkGA1UEBhMCVVMxETAPBgNVBAgTCENv\nbG9yYWRvMRIwEAYDVQQKEwlTbmFwTG9naWMxETAPBgNVBAsTCFNuYXBUZWFtMQsw\nCQYDVQQDFAIuKjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKc0/ks+\nCACBcXzo7ntfURXvUZSxuq2jl3GEhdULVdn2+cmmTbloV+VvTJ3WliOdrie5W+iw\nBKi7RIpLGJ4YipcRjp55BDIk+TzOvVEcVMxx2YsHdrjfTU4UpZkMWcb4yhlJaFFB\nXHGwncdx4KdsvEO7nB8j2pidjiSkzY1lVQM7jlbp/2nsEy38X+Luxz+h2gKrT6Cx\nmkOuvf5zPlOIxsCAugf0uupHqEavzsPunhwxJkqfuhzdtn0chUFJeU89I2a684Iy\npFKqQfUT6yVA/Q0skHXm7YnxEYYqt4WX0DbZVX40TJlLUZCdehqss3eI/uanT9qZ\nVelhn/1OPMq2sJsCAwEAATANBgkqhkiG9w0BAQ0FAAOCAQEAHG9er49xSvHDvdB+\nGfpbsuiw/HDaF+PABd0Rg5H8TsfJfeIqrfUYiZwxLi2e8fqUusZPLfTDAlCAPGYQ\n+i5SSUP3RHc0GInQBZY797saOmRyQglZoIPMyVjxveKnrRdqppR/bOlhfCiV5lTn\nNUmQTbqTbJdWlFEl5s/HOQt9qL9Z+T6DJhyjHq833xEDyyvWABL8+/ENJhczKAsv\nxbauqUAb69H8CI8XmIJVlBE7kipuus8djYObaRd38oo9iB9wvNGc0NpTnt1UQkHA\nNGaajwTO4O8l1Ahrdfufy8Cei7B5Cjo79rmeBJE0+tdpKx3P5zPzBLdhGZIDWlly\nDtG/qw==\n-----END CERTIFICATE-----\n" }' https://localhost:9443/v1/cluster/update_cert
      # creating sample DB
      echo Creating a DB
      create_db=$(curl -k POST -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d '{"name": "db2", "type": "redis", "memory_size": 1073}' -w "%{http_code}\n" -u "extensions@appdynamics.com:123456" https://localhost:9443/v1/bdbs -o /dev/null)
      if [ $code -eq  200 ]
      then
        echo Done Creating DB
      else
        echo $create_db
      fi
    fi
else
   echo Redis Enterprise is not up
fi

