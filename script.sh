sleep 30
ipaddr=$(curl http://localhost:8086/v1/bootstrap |\jq '.local_node_info.available_addresses[0].address')
ipaddr1=$(echo $ipaddr | tr -d '"')
echo $ipaddr1
# creating a cluster and node
curl -X POST -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d '{"action":"create_cluster","cluster":{"nodes":[],"name":"cluster.local","clobber":true},"node":{"paths":{"persistent_path":"/var/opt/redislabs/persist","ephemeral_path":"/var/opt/redislabs/tmp"},"identity":{"addr":"'"$ipaddr1"'"}},"license":"","credentials":{"username":"extensions@appdynamics.com","password":"123456"}}' http://localhost:8086/v1/bootstrap/create_cluster
sleep 20
code=$(curl -w "%{http_code}\n" -u "extensions@appdynamics.com:123456" http://localhost:8086/v1/cluster -o /dev/null)
echo $code
if [ $code ==  200 ]
then
  echo Creating a DB
  create_db=$(curl POST -H 'Content-Type: application/json' -H 'cache-control: no-cache' -d '{"name": "db2", "type": "redis", "memory_size": 1073}' -w "%{http_code}\n" -u "extensions@appdynamics.com:123456" http://localhost:8086/v1/bdbs -o /dev/null)
  if [ $code ==  200 ]
  then
    echo Done Creating DB
  else
    echo $create_db
  fi
fi

