package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

/**
 * @author: Vishaka Sekar on 7/22/19
 */
public class ObjectMetricsCollectorTask implements  Runnable {
    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(ObjectMetricsCollectorTask.class);
    private MonitorContextConfiguration configuration;
    private Stat statistic;
    private List<String> objectNames;
    private String displayName;
    private String uri;
    private MetricWriteHelper metricWriteHelper;
    private Phaser phaser;


    public ObjectMetricsCollectorTask (String displayName, String uri, List<String> objectNames, Stat statistic, MonitorContextConfiguration configuration, MetricWriteHelper metricWriteHelper, Phaser phaser){
        this.configuration = configuration;
        this.objectNames = objectNames;
        this.statistic = statistic;
        this.displayName = displayName;
        this.uri = uri;
        this.metricWriteHelper = metricWriteHelper;
        this.phaser = phaser;
        phaser.register();
    }

    @Override
    public void run () {
        collectMetrics(displayName, uri, objectNames, statistic);
        phaser.arriveAndDeregister();
    }

    private void collectMetrics (String displayName, String uri, List<String> objectNames, Stat statistic) {
            if(statistic.getStatsUrl() != null) {
                String statsUrl = uri + statistic.getStatsUrl();
                if (!objectNames.isEmpty()) {
                    collectObjectMetrics(displayName, uri, objectNames, statistic, statsUrl);
                }
            }
            else {
                LOGGER.debug("Please provide statistic url {}", statistic.getName());
            }
    }

    private void collectObjectMetrics (String displayName, String uri, List<String> objectNames, Stat statistic, String statsUrl) {
        ArrayNode jsonNodes;
        if(statistic.getUrl() != null && !statistic.getUrl().isEmpty() && statistic.getId()!= null && !statistic.getId().isEmpty()) {
            String url = uri + statistic.getUrl();
            jsonNodes = HttpClientUtils.getResponseAsJson(this.configuration.getContext().getHttpClient(), url, ArrayNode.class);
            if(jsonNodes != null){
                Map<String, String> IDtoObjectNameMap = findIdOfObjectNames(jsonNodes, objectNames, statistic.getId(), statistic.getName(), statistic.getType());
                if(IDtoObjectNameMap.size() > 0) {
                    for (Map.Entry<String, String> IDObjectNamePair : IDtoObjectNameMap.entrySet()) {
                        String objectId =  IDObjectNamePair.getKey();
                        String objectName =  IDObjectNamePair.getValue();
                        LOGGER.debug("Starting metric collection for object [{}] [{}] with id [{}] in [{}]", statistic.getType(), objectName, objectId, displayName);
                        ObjectMetricsCollectorSubTask task = new ObjectMetricsCollectorSubTask(displayName, statsUrl, objectId, objectName,
                                configuration, metricWriteHelper, statistic, phaser);
                        configuration.getContext().getExecutorService().execute(statistic.getType() + " task - " + IDObjectNamePair.getValue(), task);
                    }
                }
                else {LOGGER.info("Did not find ID for the objectNames [{}]", objectNames);}
            }
        }else{
            LOGGER.info("url for stat {} is null or empty. Please provide the url ", statistic.getName());
        }
    }

    private Map<String, String> findIdOfObjectNames (ArrayNode jsonNodes, List<String> objectNames, String id, String statNameFromMetricsXml, String statType) {
        Map<String, String> idToObjectNameMap = new HashMap<>();
        for (String objectName : objectNames) {
            for (JsonNode jsonNode : jsonNodes) {
                if (isObjectFoundInRedis(statNameFromMetricsXml, objectName, jsonNode) && isActive(objectName, jsonNode, statType)) {
                    if(jsonNode.get(id) != null) {
                        String key = jsonNode.get(id).isTextual() ? jsonNode.get(id).getTextValue() : jsonNode.get(id).toString();
                        String value = jsonNode.get(statNameFromMetricsXml).isTextual() ? jsonNode.get(statNameFromMetricsXml).getTextValue() : jsonNode.get(statNameFromMetricsXml).toString();
                        idToObjectNameMap.put(key, value);
                    }
                    else{
                        LOGGER.debug("There is no field called [{}] for [{}] is not found", id, objectName);
                    }
                } else {
                    LOGGER.info("Object [{}] not found in Redis Enterprise Cluster - [{}]", objectName, displayName);
                }
            }
        }
        return idToObjectNameMap;
    }

    private boolean isObjectFoundInRedis (String name, String objectName, JsonNode jsonNode) {
        return jsonNode.get(name).getTextValue().equals(objectName);
    }

    private boolean isActive (String objectName, JsonNode jsonNode, String statType){

        if(jsonNode.get("status").getTextValue().equalsIgnoreCase("active")){
            LOGGER.info("Object [{}] is in active state",objectName);
            metricWriteHelper.printMetric(configuration.getMetricPrefix() + "|" + displayName + "|" + statType + "|"+ objectName + "|" + "Status", "1", "OBSERVATION", "CURRENT", "INDIVIDUAL");
            return true;
        }
        else{
            LOGGER.info("Object [{}] is in [{}] state, not collection metrics", objectName, jsonNode.get("status").getTextValue() );
            metricWriteHelper.printMetric(configuration.getMetricPrefix() + "|" + displayName + "|" + statType + "|" + objectName +"|" + "Status" , "0", "OBSERVATION", "CURRENT", "INDIVIDUAL");
            return false;
        }

    }

}
