package com.appdynamics.extensions.redis_enterprise.metrics;
import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContext;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.executorservice.MonitorExecutorService;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.metrics.MetricCharSequenceReplacer;
import com.appdynamics.extensions.redis_enterprise.config.Metric;
import com.appdynamics.extensions.redis_enterprise.config.Stat;
import com.appdynamics.extensions.util.MetricPathUtils;
import com.appdynamics.extensions.yml.YmlReader;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author: {Vishaka Sekar} on {2019-08-06}
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({HttpClientUtils.class})
public class ObjectMetricsCollectorTaskTest {

    MonitorContextConfiguration configuration;
    MetricWriteHelper metricWriteHelper;
    Phaser phaser  = new Phaser();
    String metricPrefix =  "Custom Metrics|Redis Enterprise";
    ArgumentCaptor<List> pathCaptor = ArgumentCaptor.forClass(List.class);

    @Before
    public void setUp(){

        configuration = mock(MonitorContextConfiguration.class);
        configuration.setConfigYml("src/test/resources/config.yml");
        metricWriteHelper = mock(MetricWriteHelper.class);
        Map<String, ?> conf = YmlReader.readFromFileAsMap(new File("src/test/resources/config.yml"));
        ABaseMonitor baseMonitor = mock(ABaseMonitor.class);
        MonitorContext context = mock(MonitorContext.class);
        Mockito.when(baseMonitor.getContextConfiguration()).thenReturn(configuration);
        Mockito.when(baseMonitor.getContextConfiguration().getContext()).thenReturn(context);
        MetricPathUtils.registerMetricCharSequenceReplacer(baseMonitor);
        MetricCharSequenceReplacer replacer = MetricCharSequenceReplacer.createInstance(conf);
        Mockito.when(context.getMetricCharSequenceReplacer()).thenReturn(replacer);
        Mockito.when(configuration.getMetricPrefix()).thenReturn(metricPrefix);
        MonitorExecutorService executorService = mock(MonitorExecutorService.class);
        when(configuration.getContext().getExecutorService()).thenReturn(executorService);
        Mockito.doNothing().when(executorService).execute(any(), any());
        phaser.register();
    }

    @Test
    public void testObjectMetricCollection(){

        Stat stat = new Stat();
        stat.setName("name");
        stat.setId("uid");
        stat.setStatsUrl("/v1/bdbs/stats/last/");
        stat.setUrl("/v1/bdbs/");
        PowerMockito.mockStatic(HttpClientUtils.class);
        when(HttpClientUtils.getResponseAsJson(any(CloseableHttpClient.class), anyString(), any(Class.class))).thenAnswer(
                new Answer() {
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ObjectMapper mapper = new ObjectMapper();
                        String url = (String) invocationOnMock.getArguments()[1];
                        File file = null;
                        if(url.contains("bdbs")) {
                            file = new File("src/test/resources/objects.json");
                        }
                        JsonNode objectNode = mapper.readValue(file, JsonNode.class);
                        return objectNode;
                    }
                });

        com.appdynamics.extensions.redis_enterprise.config.Metric[] childMetrics = new Metric[1];
        com.appdynamics.extensions.redis_enterprise.config.Metric childMetric = new com.appdynamics.extensions.redis_enterprise.config.Metric();
        childMetric.setAttr("metric1");
        childMetric.setAlias("metric1");
        childMetrics[0] = childMetric;

        stat.setMetric(childMetrics);
        List<String> objectNames = new ArrayList<>();
        objectNames.add("test");

        ObjectMetricsCollectorTask objectMetricsCollectorTask = new ObjectMetricsCollectorTask("displayname", "localhost:8080", objectNames, stat, configuration, metricWriteHelper, phaser );
        objectMetricsCollectorTask.run();

        //todo : assert for method calls
    }



}
