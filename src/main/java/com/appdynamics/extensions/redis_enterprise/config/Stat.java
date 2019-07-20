package com.appdynamics.extensions.redis_enterprise.config;
import com.appdynamics.extensions.redis_enterprise.utils.Constants;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * @author: {Vishaka Sekar} on {7/17/19}
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Stat {

    @XmlAttribute
    private String alias;

    @XmlElement(name = Constants.METRIC)
    private Metric[] metric;

    @XmlAttribute(name = Constants.STATS_URL)
    private String statsUrl;

    @XmlAttribute(name = Constants.NAME)
    private String name;

    @XmlAttribute(name = Constants.ID)
    private String id;

    @XmlAttribute(name = Constants.URL)
    private String url;

    public Metric[] getMetric () {
        return metric;
    }

    public void setMetric (Metric[] metric) {
        this.metric = metric;
    }

    public String getAlias () {
        return alias;
    }

    public void setAlias (String alias) {
        this.alias = alias;
    }

    public String getUrl () {
        return url;
    }

    public void setUrl (String url) {
        this.url = url;
    }

    public String getStatsUrl () {
        return statsUrl;
    }

    public void setStatsUrl (String statsUrl) {
        this.statsUrl = statsUrl;
    }

    public String getName () {
        return name;
    }

    public void setName (String name) {
        this.name = name;
    }

    public String getId () {
        return id;
    }

    public void setId (String id) {
        this.id = id;
    }
}
