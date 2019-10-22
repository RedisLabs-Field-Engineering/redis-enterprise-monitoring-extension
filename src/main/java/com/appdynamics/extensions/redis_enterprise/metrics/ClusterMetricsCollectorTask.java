package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.redis_enterprise.config.Stats;
import org.codehaus.jackson.JsonNode;

import java.util.List;
import java.util.concurrent.Phaser;

/**
 * @author: Vishaka Sekar on 7/26/19
 */
public class ClusterMetricsCollectorTask implements Runnable {

    private String displayName;
    private String uri;
    private Phaser phaser;
    private MonitorContextConfiguration configuration;
    private MetricWriteHelper metricWriteHelper;


    public ClusterMetricsCollectorTask(String displayName, String uri, MonitorContextConfiguration configuration, MetricWriteHelper metricWriteHelper, Phaser phaser){
        this.displayName = displayName;
        this.uri = uri;
        this.phaser = phaser;
        this.configuration = configuration;
        this.metricWriteHelper = metricWriteHelper;
        phaser.register();
    }

    @Override
    public void run () {
        Stats stats = (Stats) configuration.getMetricsXml();
        Stat[] stat = stats.getStat();
        collectMetrics(stat);
        phaser.arriveAndDeregister();
    }

    private void collectMetrics(Stat[] stats){
        for (Stat parentStat : stats) {
            if (parentStat.getType().equals("cluster")) {
                JsonNode response = HttpClientUtils.getResponseAsJson(configuration.getContext().getHttpClient(), uri + parentStat.getStatsUrl(), JsonNode.class);
                ParseApiResponse parseApiResponse = new ParseApiResponse(response, configuration.getMetricPrefix() + "|" + displayName);
                List<Metric> metrics = parseApiResponse.extractMetricsFromApiResponse(parentStat, response);
                metricWriteHelper.transformAndPrintMetrics(metrics);
            }
        }
    }
}