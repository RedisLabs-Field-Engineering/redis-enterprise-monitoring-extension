package com.appdynamics.extensions.redis_enterprise;
import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContext;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.metrics.MetricCharSequenceReplacer;
import com.appdynamics.extensions.redis_enterprise.config.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stats;
import com.appdynamics.extensions.redis_enterprise.metrics.ObjectMetricsCollectorSubTask;
import com.appdynamics.extensions.util.MetricPathUtils;
import com.appdynamics.extensions.yml.YmlReader;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Phaser;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author: {Vishaka Sekar} on {7/22/19}
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({HttpClientUtils.class, MetricPathUtils.class})

public class ObjectMetricsCollectorSubTaskTest {

    MonitorContextConfiguration configuration;
    MetricWriteHelper metricWriteHelper;
    Phaser phaser  = new Phaser();
    String metricPrefix =  "Custom Metrics|RedisEnterprise";

    @Before
    public void setUp(){

        configuration = mock(MonitorContextConfiguration.class);
        configuration.setConfigYml("src/test/resources/config.yml");
        configuration.setMetricXml("src/test/resources/metrics.xml", Stats.class);
        metricWriteHelper = mock(MetricWriteHelper.class);
        Map<String, ?> conf = YmlReader.readFromFileAsMap(new File("src/test/resources/config.yml"));
        ABaseMonitor baseMonitor = mock(ABaseMonitor.class);
        MonitorContext context = mock(MonitorContext.class);
        PowerMockito.mockStatic(MetricPathUtils.class);
        when(baseMonitor.getContextConfiguration()).thenReturn(configuration);
        when(baseMonitor.getContextConfiguration().getContext()).thenReturn(context);
        MetricPathUtils.registerMetricCharSequenceReplacer(baseMonitor);
        MetricCharSequenceReplacer replacer = MetricCharSequenceReplacer.createInstance(conf);
        when(context.getMetricCharSequenceReplacer()).thenReturn(replacer);
        when(configuration.getMetricPrefix()).thenReturn(metricPrefix);
        phaser.register();
    }

    @Test
    public void metricStats(){
        String displayName = "myCluster";
        String uid = "3";
        String url = "https://localhost:9443/v1/bdbs/stats/last/3";
        String objectName = "test";

        PowerMockito.mockStatic(HttpClientUtils.class);
        when(HttpClientUtils.getResponseAsJson(any(CloseableHttpClient.class), anyString(), any(Class.class))).thenAnswer(
                new Answer() {
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ObjectMapper mapper = new ObjectMapper();
                        File file = new File("src/test/resources/channelscopy.json");
                        JsonNode objectNode = mapper.readValue(file, JsonNode.class);
                        return objectNode;
                    }
                });
        Metric[] metrics = new Metric[1];
        Metric metric = new Metric();
        metric.setAttr("conns");
        metric.setAlias("conns");
        metrics[0] = metric;
        ObjectMetricsCollectorSubTask objectMetricsCollectorSubTask = new ObjectMetricsCollectorSubTask(displayName, url, uid, objectName, configuration, metricWriteHelper, metrics, phaser);
        objectMetricsCollectorSubTask.run();

    }



}
