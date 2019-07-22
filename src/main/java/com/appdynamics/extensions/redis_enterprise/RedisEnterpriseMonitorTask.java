package com.appdynamics.extensions.redis_enterprise;
import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.redis_enterprise.config.Stats;
import com.appdynamics.extensions.redis_enterprise.metrics.StatTask;
import com.appdynamics.extensions.redis_enterprise.utils.Constants;
import com.google.common.collect.Lists;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

/**
 * @author: {Vishaka Sekar} on {7/11/19}
 */

public class RedisEnterpriseMonitorTask implements AMonitorTaskRunnable {
    private static final Logger LOGGER = ExtensionsLoggerFactory.getLogger(RedisEnterpriseMonitorTask.class);
    private final MonitorContextConfiguration configuration;
    private final Map<String, ?> server;
    private final MetricWriteHelper metricWriteHelper;
    private Phaser phaser;

    RedisEnterpriseMonitorTask (MetricWriteHelper metricWriteHelper, MonitorContextConfiguration configuration, Map<String, ?> server) {
        this.configuration = configuration;
        this.server = server;
        this.metricWriteHelper = metricWriteHelper;
        this.phaser = new Phaser();
        phaser.register();
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
                LOGGER.info("Starting metric collection for object [{}] on server [{}]", object.getKey(), displayName);
                if (!((List<String>) object.getValue()).isEmpty()) {
                    collectStatMetrics(displayName, uri, object.getKey().toString(), (List<String>) object.getValue());
                } else {
                    LOGGER.info("No object names of type [{}] found in config.yml", object.getKey());
                }
            }
            collectStatMetrics(displayName, uri, "cluster", Lists.newArrayList());
            LOGGER.debug("Finished all metrics collection for {}", displayName);
        }
        phaser.arriveAndAwaitAdvance();
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
        Stats stats = (Stats) configuration.getMetricsXml();
        Stat[] stat = stats.getStat();
        for (Stat statistic : stat) {
            if (objectType.equals(statistic.getType())) {
                StatTask statTask = new StatTask(displayName, uri, names, statistic, configuration, metricWriteHelper, phaser);
                configuration.getContext().getExecutorService().execute(statistic.getType() + " task - " , statTask);
            }

        }
    }



    @Override
    public void onTaskComplete () {
        LOGGER.info("All tasks for host [{}] finished", server.get(Constants.DISPLAY_NAME));
    }
}
