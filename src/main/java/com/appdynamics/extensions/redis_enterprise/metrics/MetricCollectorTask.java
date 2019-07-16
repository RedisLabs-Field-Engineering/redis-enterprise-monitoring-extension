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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: {Vishaka Sekar} on {7/14/19}
 */
public class MetricCollectorTask implements Runnable{

    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(MetricCollectorTask.class);
    private MonitorContextConfiguration monitorContextConfiguration;
    private String uid;
    private String name;
    private String statsEndpointUrl;
    private MetricWriteHelper metricWriteHelper;
    private String serverName;
    private String metricType;

    public MetricCollectorTask (String displayName, String statsEndpointUrl,String uid, String name,
                                MonitorContextConfiguration monitorContextConfiguration, MetricWriteHelper metricWriteHelper, String metricType) {
       this.uid = uid;
       this.name = name;
       this.statsEndpointUrl = statsEndpointUrl;
       this.monitorContextConfiguration = monitorContextConfiguration;
       this.metricWriteHelper = metricWriteHelper;
       this.serverName = displayName;
       this.metricType = metricType;
    }

    @Override
    public void run () {
        //todo: null checks
        //todo: logging
        CloseableHttpClient httpClient = monitorContextConfiguration.getContext().getHttpClient();
        Map<String, String> metrics = null;
        if(!metricType.equalsIgnoreCase("clusterMetrics")) {
            metrics = (LinkedHashMap<String, String>) HttpClientUtils.getResponseAsJson(httpClient, statsEndpointUrl, HashMap.class).get(uid);
        }
        else if(metricType.equalsIgnoreCase("clusterMetrics")){
             metrics = (HashMap<String, String>) HttpClientUtils.getResponseAsJson(httpClient, statsEndpointUrl, HashMap.class);
        }
        List<Metric> metricsList = extractMetricsFromApiResponse(metrics, metricType);
        metricWriteHelper.transformAndPrintMetrics(metricsList);
    }

    private List<Metric> extractMetricsFromApiResponse (Map<String,String> metrics, String metricType) {
        List<Metric> metricList = Lists.newArrayList();
        String[] metricPathTokens = null;
        String metricPrefix = null;
        if(!metricType.equalsIgnoreCase("clusterMetrics")) {
            metricPrefix = monitorContextConfiguration.getMetricPrefix() + "|" + serverName + "|" + name;
        }
        else if(metricType.equalsIgnoreCase("clusterMetrics")){
            metricPrefix = monitorContextConfiguration.getMetricPrefix() + "|" + serverName;
        }
        Map<String, Map<String, ?>> metricsFromConfig = (Map<String, Map<String, ?>>) monitorContextConfiguration.getConfigYml().get(metricType);
        for (Map.Entry metricFromRedis : metrics.entrySet()) {
            if (metricsFromConfig.containsKey(metricFromRedis.getKey())) {
                LOGGER.debug("Processing metric {} of metricType {} ", metricFromRedis.getKey(), metricType);
                metricPathTokens = metricFromRedis.getKey().toString().split("\\|");
                Metric metric = new Metric(metricFromRedis.getKey().toString(), metricFromRedis.getValue().toString(),
                        metricsFromConfig.get(metricFromRedis.getKey()), metricPrefix, metricPathTokens);
                metricList.add(metric);
            }
        }
        return metricList;
    }
}
