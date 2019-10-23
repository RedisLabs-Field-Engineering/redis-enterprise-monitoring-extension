package com.appdynamics.extensions.redis_enterprise.metrics;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.util.JsonUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
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
        try {
            collectMetrics(displayName, uri, objectNames, statistic);
        }catch(Exception e ){
            LOGGER.info("Exception while collecting metrics for server {} - {}", displayName , e);
        } finally {
            phaser.arriveAndDeregister();
        }

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
        ArrayNode objectDetailsJson;
        JsonNode objectsStatsJson;
        if(statistic.getUrl() != null && !statistic.getUrl().isEmpty() && statistic.getId()!= null && !statistic.getId().isEmpty()) {
            String url = uri + statistic.getUrl();
            objectDetailsJson = HttpClientUtils.getResponseAsJson(this.configuration.getContext().getHttpClient(), url, ArrayNode.class);
            if(objectDetailsJson != null){
                objectsStatsJson = HttpClientUtils.getResponseAsJson(this.configuration.getContext().getHttpClient(), statsUrl,  JsonNode.class);
                List<Pair<String, String>> IDObjectNamePairs = findIdOfObjectNames(objectDetailsJson, objectNames, statistic.getId(), statistic.getName(), statistic.getType());
                    for (Pair<String, String> IDObjectNamePair : IDObjectNamePairs) {
                        String objectId =  IDObjectNamePair.getKey();
                        String objectName =  IDObjectNamePair.getValue();
                        LOGGER.debug("Starting metric collection for object [{}] [{}] with id [{}] in [{}]", statistic.getType(), objectName, objectId, displayName);
                        JsonNode objectStats = JsonUtils.getNestedObject(objectsStatsJson, objectId);
                        ObjectMetricsCollectorSubTask task = new ObjectMetricsCollectorSubTask(displayName, statsUrl, objectId, objectName,
                                configuration, metricWriteHelper, statistic, objectStats, phaser);
                        configuration.getContext().getExecutorService().execute(statistic.getType() + " task - " + IDObjectNamePair.getValue(), task);
                    }
                }
                else {
                    LOGGER.info("Did not find ID for the objectNames [{}]", objectNames);
                }
            }
    }

    private List<Pair<String, String>> findIdOfObjectNames (ArrayNode jsonNodes,
                                                            List<String> objectNamePatterns,
                                                            String id,
                                                            String statNameFromMetricsXml,
                                                            String statType) {
        List<Pair<String, String>> idObjectNamePairs = new ArrayList<>();
        for (String objectNamePattern : objectNamePatterns) {
            Pair<String, String> idObjectNamePair = getObjectNameAndId(statNameFromMetricsXml, statType, objectNamePattern, id, jsonNodes);
            if(!idObjectNamePair.getKey().equals( "-1")){
                idObjectNamePairs.add(idObjectNamePair);
            }
            else{
                LOGGER.info("[{}] not found in Redis Enterprise server {}",objectNamePattern, displayName);
            }

        }
        return idObjectNamePairs;
    }

    private Pair<String, String> getObjectNameAndId(String statNameFromMetricsXml, String statType,
                                                    String objectNamePattern,
                                                    String idAttributeFromMetricsXml,
                                                    ArrayNode jsonNodes) {
        for (JsonNode jsonNode : jsonNodes) {
            if(jsonNode.get(statNameFromMetricsXml).getTextValue().matches(objectNamePattern)){
                String objectName = jsonNode.get(statNameFromMetricsXml).getTextValue();
                LOGGER.debug("Wildcard match for {}", objectName);
                if(isActive(objectName, jsonNode, statType)){
                    if(jsonNode.get(idAttributeFromMetricsXml) != null) {
                        String idNumber = jsonNode.get(idAttributeFromMetricsXml).isTextual() ?
                                jsonNode.get(idAttributeFromMetricsXml).getTextValue() : jsonNode.get(idAttributeFromMetricsXml).toString();
                        return new ImmutablePair<>(idNumber, objectName);
                    }
                    else{
                        LOGGER.debug("The field called [{}] for [{}] is not found", idAttributeFromMetricsXml, objectName);
                    }
                }
                else{
                    LOGGER.debug("Object [{}] not active", objectName);
                }
            }
        }
        LOGGER.info("The pattern [{}] did not match any active object in Redis Enterprise server {}", objectNamePattern, displayName);
        return new ImmutablePair<>("-1", "NaN");
    }

    private boolean isActive (String objectName, JsonNode jsonNode, String statType){

        if(jsonNode.get("status").getTextValue().equalsIgnoreCase("active")){
            LOGGER.info("Object [{}] is in active state",objectName);
            metricWriteHelper.printMetric(configuration.getMetricPrefix() + "|" +
                    displayName + "|" + statType + "|"+ objectName + "|" +
                    "Status", "1", "OBSERVATION", "CURRENT", "INDIVIDUAL");
            return true;
        }
        else{
            LOGGER.info("Object [{}] is in [{}] state, not collection metrics", objectName, jsonNode.get("status").getTextValue() );
            metricWriteHelper.printMetric(configuration.getMetricPrefix() + "|" +
                    displayName + "|" + statType + "|" + objectName +"|"
                    + "Status" , "0", "OBSERVATION", "CURRENT", "INDIVIDUAL");
            return false;
        }
    }
}
