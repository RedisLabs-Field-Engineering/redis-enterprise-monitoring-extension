package com.appdynamics.extensions.redis_enterprise;
import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.redis_enterprise.config.Stats;
import com.appdynamics.extensions.redis_enterprise.metrics.MetricCollectorTask;
import com.appdynamics.extensions.redis_enterprise.utils.Constants;
import com.google.common.collect.Lists;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: {Vishaka Sekar} on {7/11/19}
 */

public class RedisEnterpriseMonitorTask implements AMonitorTaskRunnable {
    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(RedisEnterpriseMonitorTask.class);
    private final MonitorContextConfiguration configuration;
    private final Map<String, ?> server;
    private final MetricWriteHelper metricWriteHelper;

    RedisEnterpriseMonitorTask ( MetricWriteHelper metricWriteHelper, MonitorContextConfiguration configuration, Map<String, ?> server) {
        this.configuration = configuration;
        this.server = server;
        this.metricWriteHelper = metricWriteHelper;
    }

    @Override
    public void run () {
        int heartBeat = getConnectionStatus(server);
        metricWriteHelper.printMetric(configuration.getMetricPrefix() + "|" + server.get(Constants.DISPLAY_NAME).toString() + "|" + "Connection Status", String.valueOf(heartBeat), "AVERAGE", "AVERAGE", "INDIVIDUAL");
        if (heartBeat == 1) {
            Map<String, ?> objects = (Map<String, ?>) server.get("objects");
            String uri = server.get(Constants.URI).toString();
            String displayName = server.get(Constants.DISPLAY_NAME).toString();
            for (Map.Entry object : objects.entrySet()) {
                LOGGER.info("Starting metric collection for server {}", displayName);
                if(!((List<String>) object.getValue()).isEmpty()) {
                    collectStatMetrics(displayName, uri, object.getKey().toString(), (List<String>) object.getValue());
                }else{
                    LOGGER.info("No object names of type [{}] found in config.yml", object.getKey());
                }
            }
            collectStatMetrics(displayName, uri, "cluster", Lists.newArrayList());
        }
    }

    private int getConnectionStatus (Map<String, ?> server) {
        HttpGet get = new HttpGet(server.get(Constants.URI).toString() + "/v1/");
        CloseableHttpResponse closeableHttpResponse = null;
        try {
            closeableHttpResponse = this.configuration.getContext().getHttpClient().execute(get);
            StatusLine statusLine = closeableHttpResponse.getStatusLine();
            if (statusLine.getStatusCode() == 200) {
                return 1;
            } else if (statusLine.getStatusCode() != 200) {
                HttpClientUtils.printError(closeableHttpResponse, server.get(Constants.URI).toString() + "/v1/");
                return 0;
            }
        } catch (IOException e) {
            LOGGER.info("Cannot connect to Cluster {} {}. Not collecting metrics", server.get(Constants.DISPLAY_NAME), e);
        } finally {
            if (closeableHttpResponse != null) {
                try {
                    closeableHttpResponse.close();
                } catch (IOException e) {
                    LOGGER.info("Error while closing connection {}", e);
                }
            }
        }
        return 0;
    }

    private void collectStatMetrics (String displayName, String uri, String objectType, List<String> names) {
        //TODO: phaser

        //todo: response null check
        //todo: logging

        Stats stats = (Stats) configuration.getMetricsXml();
        Stat[] stat = stats.getStat();
        for (Stat statistic : stat) {
            collectMetrics(displayName, uri, objectType, names, statistic);
        }
    }

    private void collectMetrics (String displayName, String uri, String objectType, List<String> names, Stat statistic) {
        if (objectType.equals(statistic.getType())) {
            String statsUrl = uri + statistic.getStatsUrl();

            if (!names.isEmpty() && !statistic.getUrl().isEmpty()) {
                ArrayNode nodeDataJson;
                String url = uri + statistic.getUrl();
                nodeDataJson = HttpClientUtils.getResponseAsJson(this.configuration.getContext().getHttpClient(), url, ArrayNode.class);
                Map<String, String> IDtoObjectNameMap = findIdOfObjectNames(nodeDataJson, names, statistic.getId(), statistic.getName());

                for (Map.Entry<String, String> IDObjectNamePair : IDtoObjectNameMap.entrySet()) {
                    LOGGER.debug("Starting metric collection for {} {} with id {}", statistic.getType(), IDObjectNamePair.getValue(), IDObjectNamePair.getKey());
                    MetricCollectorTask task = new MetricCollectorTask(displayName, statsUrl, IDObjectNamePair.getKey(), IDObjectNamePair.getValue(),
                            configuration, metricWriteHelper, statistic.getMetric());
                    configuration.getContext().getExecutorService().execute(statistic.getType() + " task - " + IDObjectNamePair.getValue(), task);
                }

            } else if (names.isEmpty() && statistic.getUrl().isEmpty()) {
                LOGGER.debug("Starting cluster metric collection for {} ",displayName);
                MetricCollectorTask task = new MetricCollectorTask(displayName, statsUrl, statistic.getId(), statistic.getName(),
                        configuration, metricWriteHelper, statistic.getMetric());
                configuration.getContext().getExecutorService().execute(" cluster task - ", task);
            }
        }
    }

    private Map<String, String> findIdOfObjectNames (ArrayNode nodeDataJson, List<String> objectNames, String id, String statNameFromMetricsXml) {
        Map<String, String> idToObjectNameMap = new HashMap<>();
        for (String objectName : objectNames) {
            for (JsonNode jsonNode : nodeDataJson) {
                if (isObjectNameInConfigYml(statNameFromMetricsXml, objectName, jsonNode)) {
                    String key = jsonNode.get(id).isTextual() ? jsonNode.get(id).getTextValue() : jsonNode.get(id).toString();
                    String value = jsonNode.get(statNameFromMetricsXml).isTextual() ? jsonNode.get(statNameFromMetricsXml).getTextValue() : jsonNode.get(statNameFromMetricsXml).toString();
                    idToObjectNameMap.put(key, value);
                } else {
                    LOGGER.info("Object [{}] not found in Redis Enterprise Cluster - [{}]", objectName, server.get(Constants.DISPLAY_NAME));
                }
            }
        }
        return idToObjectNameMap;
    }

    private boolean isObjectNameInConfigYml (String name, String objectName, JsonNode jsonNode) {
        return jsonNode.get(name).getTextValue().equals(objectName);
    }

    @Override
    public void onTaskComplete () {
        //TODO:  and logging
    }
}
