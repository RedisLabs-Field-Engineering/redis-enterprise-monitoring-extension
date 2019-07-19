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
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

/**
 * @author: {Vishaka Sekar} on {7/11/19}
 */

public class RedisEnterpriseMonitorTask implements AMonitorTaskRunnable {
    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(RedisEnterpriseMonitorTask.class);
    private final MonitorContextConfiguration monitorContextConfiguration;
    private final Map<String, ?> server;
    private MetricWriteHelper metricWriteHelper;
    private Map<String, String> connectionMap;
    private Phaser phaser = new Phaser();

    RedisEnterpriseMonitorTask(MetricWriteHelper metricWriteHelper, MonitorContextConfiguration monitorContextConfiguration, Map<String, ?> server, Map<String, String> connectionMap){
        this.monitorContextConfiguration = monitorContextConfiguration;
        this.server = server;
        this.metricWriteHelper = metricWriteHelper;
        this.connectionMap = connectionMap;
        phaser.register();
    }

    @Override
    public void run () {
      //TODO:Phaser
        // TODO:connection close
        phaser.arriveAndAwaitAdvance();
        connectionMap.put("uri", server.get("uri").toString());
        connectionMap.put(("useSSL"), server.get("useSSL").toString());
        Map<String, ?> objects = (Map<String, ?>) server.get("objects");


        for(Map. Entry object : objects.entrySet()) {
            LOGGER.info("Starting metric collection for server {}", server.get(Constants.DISPLAY_NAME));
            collectMetrics(server.get("displayName").toString(), connectionMap.get("uri"), object.getKey().toString(), (List<String>)object.getValue());
        }
        collectMetrics(server.get("displayName").toString(), connectionMap.get("uri"), "cluster", Lists.newArrayList());
        phaser.arriveAndDeregister();
    }

    private void collectMetrics (String displayName, String uri, String metricType, List<String> names) {

        //TODO: phaser
        //todo: response status code
        //todo: response null check
        //todo: logging
        //todo: constants

        Stats stats = (Stats) monitorContextConfiguration.getMetricsXml();
        Stat[] stat = stats.getStat();

        for (Stat statistic : stat) {
            if (metricType.equals(statistic.getAlias())) {
                String statsUrl = uri + statistic.getStatsUrl();

                if(!names.isEmpty() && !statistic.getUrl().isEmpty()) {
                    ArrayNode nodeDataJson;
                    String url = uri + statistic.getUrl();
                    nodeDataJson = HttpClientUtils.getResponseAsJson(this.monitorContextConfiguration.getContext().getHttpClient(), url, ArrayNode.class);

                    Map<String, String> keyToNameMap = constructKeyToNameMapping(nodeDataJson, names, statistic.getKey(), statistic.getName());
                    for (Map.Entry<String, String> keyToName : keyToNameMap.entrySet()) {
                        MetricCollectorTask task = new MetricCollectorTask(displayName, statsUrl, keyToName.getKey(), keyToName.getValue(),
                                monitorContextConfiguration, metricWriteHelper, statistic.getMetric());
                        monitorContextConfiguration.getContext().getExecutorService().execute(" task - " + keyToName.getValue(), task);
                    }
                }
                else if (names.isEmpty() && statistic.getUrl().isEmpty() ){
                    MetricCollectorTask task = new MetricCollectorTask(displayName, statsUrl, statistic.getKey(), statistic.getName(),
                            monitorContextConfiguration, metricWriteHelper, statistic.getMetric());
                    monitorContextConfiguration.getContext().getExecutorService().execute(" cluster task - " , task);
                }
            }
        }
    }

    private Map<String,String> constructKeyToNameMapping (ArrayNode nodeDataJson, List<String> objectNames, String key, String name) {
        Map<String, String> keyToNameMap = new HashMap<>();
        for(String node : objectNames) {
            for (JsonNode jsonNode : nodeDataJson) {
                if (jsonNode.get(name).getTextValue().equals(node)){
                    if(jsonNode.get(key).isTextual()) {
                        keyToNameMap.put(jsonNode.get(key).getTextValue(), jsonNode.get(name).getTextValue());
                    }
                    else
                        keyToNameMap.put(jsonNode.get(key).toString(), jsonNode.get(name).getTextValue());
                }
                else{
                    LOGGER.info("Database {} not found in Redis Enterprise", node);
                }
            }
        }
        return keyToNameMap;
    }

    @Override
    public void onTaskComplete () {
        //TODO: heartbeat and logging
    }

}
