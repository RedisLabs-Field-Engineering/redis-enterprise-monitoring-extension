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
 * @author: {Vishaka Sekar} on {7/22/19}
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
            String statsUrl = uri + statistic.getStatsUrl();
            if (!objectNames.isEmpty() && !statistic.getUrl().isEmpty()) {
                collectObjectMetrics(displayName, uri, objectNames, statistic, statsUrl);
            }
    }

    private void collectObjectMetrics (String displayName, String uri, List<String> objectNames, Stat statistic, String statsUrl) {
        ArrayNode jsonNodes;
        String url = uri + statistic.getUrl();
        jsonNodes = HttpClientUtils.getResponseAsJson(this.configuration.getContext().getHttpClient(), url, ArrayNode.class);
        Map<String, String> IDtoObjectNameMap = findIdOfObjectNames(jsonNodes, objectNames, statistic.getId(), statistic.getName());
        for (Map.Entry<String, String> IDObjectNamePair : IDtoObjectNameMap.entrySet()) {
            LOGGER.debug("Starting metric collection for object [{}] [{}] with id [{}] in [{}]", statistic.getType(), IDObjectNamePair.getValue(), IDObjectNamePair.getKey(), displayName);
            ObjectMetricsCollectorSubTask task = new ObjectMetricsCollectorSubTask(displayName, statsUrl, IDObjectNamePair.getKey(), IDObjectNamePair.getValue(),
                    configuration, metricWriteHelper, statistic.getMetric(), phaser);
            configuration.getContext().getExecutorService().execute(statistic.getType() + " task - " + IDObjectNamePair.getValue(), task);
        }
    }

    private Map<String, String> findIdOfObjectNames (ArrayNode jsonNodes, List<String> objectNames, String id, String statNameFromMetricsXml) {
        Map<String, String> idToObjectNameMap = new HashMap<>();
        for (String objectName : objectNames) {
            for (JsonNode jsonNode : jsonNodes) {
                if (isObjectFoundInRedis(statNameFromMetricsXml, objectName, jsonNode)) {
                    String key = jsonNode.get(id).isTextual() ? jsonNode.get(id).getTextValue() : jsonNode.get(id).toString();
                    String value = jsonNode.get(statNameFromMetricsXml).isTextual() ? jsonNode.get(statNameFromMetricsXml).getTextValue() : jsonNode.get(statNameFromMetricsXml).toString();
                    idToObjectNameMap.put(key, value);
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
}
