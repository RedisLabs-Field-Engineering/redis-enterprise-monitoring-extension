package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

/**
 * @author: {Vishaka Sekar} on {7/14/19}
 */
public class ObjectMetricsCollectorSubTask implements Runnable {

    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(ObjectMetricsCollectorSubTask.class);
    private final MonitorContextConfiguration monitorContextConfiguration;
    private final String uid;
    private final String objectName;
    private final String statsEndpointUrl;
    private final MetricWriteHelper metricWriteHelper;
    private final String serverName;

    private com.appdynamics.extensions.redis_enterprise.config.Metric[] metricsFromConfig;
    private Phaser phaser;

    public ObjectMetricsCollectorSubTask (String displayName, String statsEndpointUrl, String uid, String objectName,
                                          MonitorContextConfiguration monitorContextConfiguration, MetricWriteHelper metricWriteHelper, com.appdynamics.extensions.redis_enterprise.config.Metric[] metricsFromConfig, Phaser phaser) {
        this.uid = uid;
        this.objectName = objectName;
        this.statsEndpointUrl = statsEndpointUrl;
        this.monitorContextConfiguration = monitorContextConfiguration;
        this.metricWriteHelper = metricWriteHelper;
        this.serverName = displayName;
        this.metricsFromConfig = metricsFromConfig;
        this.phaser = phaser;
        this.phaser.register();
    }

    @Override

    public void run () {
        CloseableHttpClient httpClient = monitorContextConfiguration.getContext().getHttpClient();
        Map<String, String> metricsApiResponse = null;

        if(!uid.isEmpty()) {
            LOGGER.debug("Extracting metricsFromConfig for [{}] ", statsEndpointUrl + uid);
            JsonNode objectNode;
            Map<String, Map<String, String>> map;
            ObjectMapper mapper = new ObjectMapper();
            objectNode = HttpClientUtils.getResponseAsJson(httpClient, statsEndpointUrl + uid, JsonNode.class);
            map = (Map<String, Map<String, String>>)mapper.convertValue(objectNode, HashMap.class);
            metricsApiResponse = map.get(uid);
        }
        ParseApiResponse parser = new ParseApiResponse(metricsApiResponse, serverName);
        List<Metric> metricsList = parser.extractMetricsFromApiResponse(metricsFromConfig);
        metricWriteHelper.transformAndPrintMetrics(metricsList);
        phaser.arriveAndDeregister();
    }

}
