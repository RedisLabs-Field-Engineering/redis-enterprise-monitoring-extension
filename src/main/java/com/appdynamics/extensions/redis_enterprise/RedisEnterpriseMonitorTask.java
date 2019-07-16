package com.appdynamics.extensions.redis_enterprise;
import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.redis_enterprise.metrics.MetricCollectorTask;
import com.appdynamics.extensions.redis_enterprise.utils.Constants;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * @author: {Vishaka Sekar} on {7/11/19}
 */

public class RedisEnterpriseMonitorTask implements AMonitorTaskRunnable {
    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(RedisEnterpriseMonitorTask.class);
    private final MonitorContextConfiguration monitorContextConfiguration;
    private final Map<String, ?> server;
    private MetricWriteHelper metricWriteHelper;


    RedisEnterpriseMonitorTask(MetricWriteHelper metricWriteHelper, MonitorContextConfiguration monitorContextConfiguration, Map<String, ?> server){
        this.monitorContextConfiguration = monitorContextConfiguration;
        this.server = server;
        this.metricWriteHelper = metricWriteHelper;
    }

    @Override
    public void run () {
      //TODO:Phaser
        // TODO:connection close
        LOGGER.info("Starting metric collection for server {}", server.get(Constants.DISPLAY_NAME));
        Map<String, String> connectionMap = (Map<String, String>)monitorContextConfiguration.getConfigYml().get("connection");
        connectionMap.put("host", server.get("host").toString());
        connectionMap.put("port", server.get("port").toString());
        connectionMap.put(("useSSL"), server.get("useSSL").toString());

        Map<String, String> endpointUrls = buildConnectionUrl(connectionMap);
        if(endpointUrls != null) {
            if(endpointUrls.get("bdbs") != null){
                collectDBMetrics(server, endpointUrls.get("bdbs"));
            }
            if(endpointUrls.get("nodes") != null){
                collectNodeMetrics(server, endpointUrls.get("nodes") );
            }
            collectClusterMetrics(server, endpointUrls.get("cluster"));
        }
        else{
            LOGGER.info("Please provide connection properties in connection section");
        }
    }

    private void collectDBMetrics (Map<String, ?> server, String dbNamesEndpointUrl ) {

        //TODO: phaser
        //todo: response status code
        //todo: response null check
        //todo: logging
        //todo: constants
        ArrayNode nodeDataJson;
        nodeDataJson = HttpClientUtils.getResponseAsJson(this.monitorContextConfiguration.getContext().getHttpClient(),
                    dbNamesEndpointUrl, ArrayNode.class);
        List<String> databaseNames = (List<String>) server.get("databaseNames");
        if(!databaseNames.isEmpty()) {
            Map<String, String> UIDToDbNameMap = constructUIDToDbNameMapping(nodeDataJson, databaseNames);
            for (Map.Entry<String, String> uidDbName : UIDToDbNameMap.entrySet()) {
                String dbStatsEndpointUrl = dbNamesEndpointUrl + "/stats/last/" + uidDbName.getKey();
                MetricCollectorTask task = new MetricCollectorTask(server.get("displayName").toString(), dbStatsEndpointUrl, uidDbName.getKey(), uidDbName.getValue(),
                        monitorContextConfiguration, metricWriteHelper, "dbMetrics" );
                monitorContextConfiguration.getContext().getExecutorService().execute(" db task - " + uidDbName.getValue(), task);
            }
        }
        else{
            LOGGER.info("Empty database names, skipping db metric collection for server {}", server.get("displayName").toString());
        }
    }

    private Map<String,String> constructUIDToDbNameMapping (ArrayNode nodeDataJson, List<String> nodes) {
        Map<String, String> UIDToDbNameMap = new HashMap<>();
        for(String node : nodes) {
            for (JsonNode jsonNode : nodeDataJson) {
                if (jsonNode.get("name").getTextValue().equals(node)){
                    UIDToDbNameMap.put(jsonNode.get("uid").toString(), jsonNode.get("name").getTextValue());
                }
                else{
                    LOGGER.info("Database {} not found in Redis Enterprise", node);
                }
            }
        }
        return UIDToDbNameMap;
    }

    private void collectNodeMetrics (Map<String, ?> server, String nodesEndpointUrl) {
        //TODO: phaser
        //todo: response status code
        //todo: response null check
        //todo: logging
        //todo: constants
        ArrayNode nodeDataJson;
        nodeDataJson = HttpClientUtils.getResponseAsJson(this.monitorContextConfiguration.getContext().getHttpClient(),
                nodesEndpointUrl, ArrayNode.class);
        List<String> nodeNames = (List<String>) server.get("nodeNames");
        if(!nodeNames.isEmpty()) {
            Map<String, String> nodeIPAddressUIDMap = constructUIDToNodeIPAddressMapping(nodeDataJson, nodeNames);
            for (Map.Entry<String, String> UIDNodeIPAddress : nodeIPAddressUIDMap.entrySet()) {
                String nodeStatsEndpointUrl = nodesEndpointUrl + "/stats/last/" + UIDNodeIPAddress.getKey();
                MetricCollectorTask task = new MetricCollectorTask(server.get("displayName").toString(),nodeStatsEndpointUrl, UIDNodeIPAddress.getKey(), UIDNodeIPAddress.getValue(),
                        monitorContextConfiguration, metricWriteHelper, "nodeMetrics" );
                monitorContextConfiguration.getContext().getExecutorService().execute(" node task - " + UIDNodeIPAddress.getValue(), task);
            }
        }
        else{
            LOGGER.info("Empty node names, skipping node metric collection for server {}", server.get("displayName").toString());
        }
    }

    private Map<String,String> constructUIDToNodeIPAddressMapping (ArrayNode nodeDataJson, List<String> nodes) {
        Map<String, String> UIDToNodeIPAddressMap = new HashMap<>();
        for(String node : nodes) {
            for (JsonNode jsonNode : nodeDataJson) {
                if (jsonNode.get("addr").getTextValue().equals(node)){
                    UIDToNodeIPAddressMap.put(jsonNode.get("uid").toString(), jsonNode.get("addr").getTextValue());
                }
                else{
                    LOGGER.info("Node {} not found in Redis Enterprise", node);
                }
            }
        }
        return UIDToNodeIPAddressMap;
    }

    private void collectClusterMetrics (Map<String,?> server, String clusterEndpointUrl) {
        String clusterStatsEndpointUrl = clusterEndpointUrl + "/stats/last";
        MetricCollectorTask task = new MetricCollectorTask(server.get("displayName").toString(), clusterStatsEndpointUrl, server.get("displayName").toString(), server.get("displayName").toString(),
                monitorContextConfiguration, metricWriteHelper, "clusterMetrics" );
        monitorContextConfiguration.getContext().getExecutorService().execute(" cluster task - " + server.get("displayName").toString(), task);
    }

    private Map<String, String> buildConnectionUrl (Map<String, String> connectionProperties){
        Map<String , String > endpointUrls = new HashMap<>();
        String url = UrlBuilder.builder(connectionProperties).build();
        endpointUrls.put("cluster", url + "/v1/cluster");
        endpointUrls.put("bdbs", url + "/v1/bdbs");
        endpointUrls.put("nodes", url + "/v1/nodes");
        return endpointUrls;
    }

    @Override
    public void onTaskComplete () {
        //TODO: heartbeat and logging
    }

}
