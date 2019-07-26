package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.metrics.Metric;
import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: {Vishaka Sekar} on {7/26/19}
 */
public class ParseApiResponse {

    Map<String, String> metricsApiResponse;
    String metricPrefix;

    public ParseApiResponse(Map<String, String> metricsApiResponse, String metricPrefix ){
        this.metricsApiResponse = metricsApiResponse;
        this.metricPrefix = metricPrefix;
    }

     List<Metric> extractMetricsFromApiResponse (com.appdynamics.extensions.redis_enterprise.config.Metric[] metricsFromConfig) {
        List<Metric> metricList = Lists.newArrayList(); //nul check for metrics apiresponse
        String[] metricPathTokens;

        for (Map.Entry<String, String> metricFromRedis : metricsApiResponse.entrySet()) {
            for (com.appdynamics.extensions.redis_enterprise.config.Metric metricFromConfig : metricsFromConfig) {
                if (metricFromConfig.getAttr().equals(metricFromRedis.getKey())) {
                    //LOGGER.debug("Processing metric [{}] ", metricFromConfig.getAttr());
                    metricPathTokens = metricFromRedis.getKey().split("\\|");
                    Map<String, Object> props = new HashMap<>();
                    props.put("aggregationType", metricFromConfig.getAggregationType());
                    props.put("clusterRollUpType", metricFromConfig.getClusterRollUpType());
                    props.put("timeRollUpType", metricFromConfig.getTimeRollUpType());
                    props.put("alias", metricFromConfig.getAlias()); //todo: convert
                    Metric metric = new Metric(metricFromRedis.getKey(), String.valueOf(metricFromRedis.getValue()),
                            props, metricPrefix, metricPathTokens);
                    metricList.add(metric);
                }
            }
        }
        return metricList;
    }
}
