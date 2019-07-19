/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */
package com.appdynamics.extensions.redis_enterprise;
import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.redis_enterprise.config.Stats;
import com.appdynamics.extensions.redis_enterprise.utils.Constants;
import com.appdynamics.extensions.util.AssertUtils;
import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author: {Vishaka Sekar} on {7/11/19}
 */
public class RedisEnterpriseMonitor extends ABaseMonitor {

    @Override
    protected String getDefaultMetricPrefix () {
        return Constants.DEFAULT_METRIC_PREFIX;
    }

    @Override
    public String getMonitorName () {
        return Constants.REDIS_ENTERPRISE;
    }

    @Override
    protected void initializeMoreStuff (Map<String, String> args) {
        this.getContextConfiguration().setMetricXml(args.get("metrics-file"), Stats.class);
    }

    @Override
    protected void doRun (TasksExecutionServiceProvider tasksExecutionServiceProvider) {
        List<Map<String, ?>> servers =  getServers();
        MonitorContextConfiguration monitorContextConfiguration = this.getContextConfiguration();
        Map<String, String> connectionMap =  (Map<String, String>)this.getContextConfiguration().getConfigYml().get("connection");

        for (Map<String, ?> server : servers) {
            RedisEnterpriseMonitorTask task = new RedisEnterpriseMonitorTask(tasksExecutionServiceProvider.getMetricWriteHelper(), this.getContextConfiguration(),server, connectionMap);
            AssertUtils.assertNotNull(server.get(Constants.DISPLAY_NAME), "The displayName can not be null");
            tasksExecutionServiceProvider.submit(server.get(Constants.DISPLAY_NAME).toString(), task);
        }
    }

    @Override
    protected List<Map<String, ?>> getServers () {
        return (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get(Constants.SERVERS);
    }

    public static void main(String[] args) throws TaskExecutionException, IOException {

        ConsoleAppender ca = new ConsoleAppender();
        ca.setWriter(new OutputStreamWriter(System.out));
        ca.setLayout(new PatternLayout("%-5p [%t]: %m%n"));
        ca.setThreshold(Level.DEBUG);
        org.apache.log4j.Logger.getRootLogger().addAppender(ca);


        RedisEnterpriseMonitor monitor = new RedisEnterpriseMonitor();
        final Map<String, String> taskArgs = Maps.newHashMap();
        taskArgs.put("config-file", "/Users/vishaka.sekar/AppDynamics/redis-enterprise-monitoring-extension/src/main/resources/config/config.yml");
        taskArgs.put("metrics-file", "/Users/vishaka.sekar/AppDynamics/redis-enterprise-monitoring-extension/src/main/resources/config/metrics.xml");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    monitor.execute(taskArgs, null);
                } catch (Exception e) {
                    e.printStackTrace();
                    //logger.error("Error while running the task", e);
                }
            }
        }, 2, 60, TimeUnit.SECONDS);

    }
}
