package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.google.common.collect.Lists;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

/**
 * @author: {Vishaka Sekar} on {7/14/19}
 */
public class MetricCollectorTask implements Runnable {

    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(MetricCollectorTask.class);
    private final MonitorContextConfiguration monitorContextConfiguration;
    private final String uid;
    private final String objectName;
    private final String statsEndpointUrl;
    private final MetricWriteHelper metricWriteHelper;
    private final String serverName;
    private com.appdynamics.extensions.redis_enterprise.config.Metric[] metrics;
    Phaser phaser;

    public MetricCollectorTask (String displayName, String statsEndpointUrl, String uid, String objectName,
                                MonitorContextConfiguration monitorContextConfiguration, MetricWriteHelper metricWriteHelper, com.appdynamics.extensions.redis_enterprise.config.Metric[] metrics, Phaser phaser) {
        this.uid = uid;
        this.objectName = objectName;
        this.statsEndpointUrl = statsEndpointUrl;
        this.monitorContextConfiguration = monitorContextConfiguration;
        this.metricWriteHelper = metricWriteHelper;
        this.serverName = displayName;
        this.metrics = metrics;
        this.phaser = phaser;
        phaser.register();
    }

    @Override

    public void run () {


        CloseableHttpClient httpClient = monitorContextConfiguration.getContext().getHttpClient();
        Map<String, String> metricsApiResponse;

        if(!uid.isEmpty()) {
            LOGGER.debug("Extracting metrics for [{}] ", statsEndpointUrl + uid);
            metricsApiResponse = (HashMap<String, String>) HttpClientUtils.getResponseAsJson(httpClient, statsEndpointUrl + uid, HashMap.class).get(uid);
        }
        else{
            LOGGER.debug("Extracting metrics for [{}] ", statsEndpointUrl);
            metricsApiResponse = (HashMap<String, String>) HttpClientUtils.getResponseAsJson(httpClient, statsEndpointUrl, HashMap.class);
        }
        List<Metric> metricsList = extractMetricsFromApiResponse(metricsApiResponse);
        metricWriteHelper.transformAndPrintMetrics(metricsList);
        phaser.arriveAndDeregister();
    }

    private List<Metric> extractMetricsFromApiResponse (Map<String, String> metricsApiResponse) {
        List<Metric> metricList = Lists.newArrayList();
        String[] metricPathTokens;
        String metricPrefix;
        metricPrefix = monitorContextConfiguration.getMetricPrefix() + "|" + serverName + "|" + objectName;
        for (Map.Entry metricFromRedis : metricsApiResponse.entrySet()) {
            for (com.appdynamics.extensions.redis_enterprise.config.Metric metricFromConfig : metrics) {
                if (metricFromConfig.getAttr().equals(metricFromRedis.getKey())) {
                    LOGGER.debug("Processing metric [{}] ", metricFromConfig.getAttr());
                    metricPathTokens = metricFromRedis.getKey().toString().split("\\|");
                    Map<String, Object> props = new HashMap<>();
                    props.put("aggregationType", metricFromConfig.getAggregationType());
                    props.put("clusterRollUpType", metricFromConfig.getClusterRollUpType());
                    props.put("timeRollUpType", metricFromConfig.getTimeRollUpType());
                    props.put("alias", metricFromConfig.getAlias()); //todo: convert
                    Metric metric = new Metric(metricFromRedis.getKey().toString(), metricFromRedis.getValue().toString(),
                            props, metricPrefix, metricPathTokens);
                    metricList.add(metric);
                }
            }
        }
        return metricList;
    }
}
