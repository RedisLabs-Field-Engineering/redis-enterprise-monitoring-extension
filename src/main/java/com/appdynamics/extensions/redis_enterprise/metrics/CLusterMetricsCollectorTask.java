package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.redis_enterprise.config.Stats;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Phaser;

/**
 * @author: {Vishaka Sekar} on {7/26/19}
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
        for (Stat statistic : stat) {
            if (statistic.getType().equals("cluster")) {
                HashMap<String, String> map = HttpClientUtils.getResponseAsJson(configuration.getContext().getHttpClient(), uri + statistic.getUrl(), HashMap.class);
                ParseApiResponse parseApiResponse = new ParseApiResponse(map, configuration.getMetricPrefix() + "|" + displayName);
                List<Metric> metrics = parseApiResponse.extractMetricsFromApiResponse(statistic.getMetric());
                metricWriteHelper.transformAndPrintMetrics(metrics);
            }
        }
        phaser.arriveAndDeregister();
    }
}