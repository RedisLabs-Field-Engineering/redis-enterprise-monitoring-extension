package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.util.JsonUtils;
import com.google.common.collect.Lists;
import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: Vishaka Sekar on 7/26/19
 */
class ParseApiResponse {

    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(ParseApiResponse.class);
    private JsonNode metricsApiResponse;
    private String metricPrefix;
    List<Metric> metricList = Lists.newArrayList();

     ParseApiResponse(JsonNode metricsApiResponse, String metricPrefix ){
        this.metricsApiResponse = metricsApiResponse;
        this.metricPrefix = metricPrefix;
    }

     List<Metric> extractMetricsFromApiResponse (Stat stat, JsonNode jsonNode) {
         String[] metricPathTokens;
         if (metricsApiResponse != null) {
             if(stat.getStats()!=null){
                 for(Stat childStat: stat.getStats()) {
                     extractMetricsFromApiResponse(childStat, JsonUtils.getNestedObject(jsonNode, childStat.getName()));
                 }
             }
             for (com.appdynamics.extensions.redis_enterprise.config.Metric metricFromConfig : stat.getMetric()) {//todo: stat.getMetric() null check

                 String value = JsonUtils.getTextValue(jsonNode, metricFromConfig.getAttr());
                 if (value != null) {
                     LOGGER.info("Processing metric [{}] ", metricFromConfig.getAttr());
                     metricPathTokens = metricFromConfig.getAttr().split("\\|");
                     Map<String, Object> props = new HashMap<>();
                     props.put("aggregationType", metricFromConfig.getAggregationType());
                     props.put("clusterRollUpType", metricFromConfig.getClusterRollUpType());
                     props.put("timeRollUpType", metricFromConfig.getTimeRollUpType());
                     props.put("alias", metricFromConfig.getAlias()); //todo: convert, multiplier
                     Metric metric = new Metric(metricFromConfig.getAttr(), value, props, metricPrefix, metricPathTokens);
                     metricList.add(metric);
                 } else {
                     LOGGER.info("Metric not found in response");
                 }
             }
             return metricList;
         }
         else{
             LOGGER.info("No metrics received from Redis Enterprise");
             return null;
         }
     }
}
