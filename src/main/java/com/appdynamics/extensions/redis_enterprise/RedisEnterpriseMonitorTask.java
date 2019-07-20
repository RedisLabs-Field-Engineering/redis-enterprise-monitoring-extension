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
                collectMetrics(displayName, uri, object.getKey().toString(), (List<String>) object.getValue());
            }
            collectMetrics(displayName, uri, "cluster", Lists.newArrayList());
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

    private void collectMetrics (String displayName, String uri, String metricType, List<String> names) {
        //TODO: phaser
        //todo: response status code
        //todo: response null check
        //todo: logging
        //todo: constants
        Stats stats = (Stats) configuration.getMetricsXml();
        Stat[] stat = stats.getStat();
        for (Stat statistic : stat) {
            collectStatMetrics(displayName, uri, metricType, names, statistic);
        }
    }

    private void collectStatMetrics (String displayName, String uri, String metricType, List<String> names, Stat statistic) {
        if (metricType.equals(statistic.getAlias())) {
            String statsUrl = uri + statistic.getStatsUrl();

            if (!names.isEmpty() && !statistic.getUrl().isEmpty()) {
                ArrayNode nodeDataJson;
                String url = uri + statistic.getUrl();
                nodeDataJson = HttpClientUtils.getResponseAsJson(this.configuration.getContext().getHttpClient(), url, ArrayNode.class);
                Map<String, String> keyToNameMap = constructKeyToNameMapping(nodeDataJson, names, statistic.getKey(), statistic.getName());
                for (Map.Entry<String, String> keyToName : keyToNameMap.entrySet()) {
                    MetricCollectorTask task = new MetricCollectorTask(displayName, statsUrl, keyToName.getKey(), keyToName.getValue(),
                            configuration, metricWriteHelper, statistic.getMetric());
                    configuration.getContext().getExecutorService().execute(statistic.getAlias() + " task - " + keyToName.getValue(), task);
                }

            } else if (names.isEmpty() && statistic.getUrl().isEmpty()) {
                MetricCollectorTask task = new MetricCollectorTask(displayName, statsUrl, statistic.getKey(), statistic.getName(),
                        configuration, metricWriteHelper, statistic.getMetric());
                configuration.getContext().getExecutorService().execute(" cluster task - ", task);
            }
        }
    }

    private Map<String, String> constructKeyToNameMapping (ArrayNode nodeDataJson, List<String> objectNames, String key, String name) {
        Map<String, String> keyToNameMap = new HashMap<>();
        for (String node : objectNames) {
            for (JsonNode jsonNode : nodeDataJson) {
                if (jsonNode.get(name).getTextValue().equals(node)) {
                    if (jsonNode.get(key).isTextual()) {
                        keyToNameMap.put(jsonNode.get(key).getTextValue(), jsonNode.get(name).getTextValue());
                    } else
                        keyToNameMap.put(jsonNode.get(key).toString(), jsonNode.get(name).getTextValue());
                } else {
                    LOGGER.info("Database {} not found in Redis Enterprise", node);
                }
            }
        }
        return keyToNameMap;
    }

    @Override
    public void onTaskComplete () {
        //TODO:  and logging
    }
}
