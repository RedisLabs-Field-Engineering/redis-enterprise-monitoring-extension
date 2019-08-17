AppDynamics Monitoring Extension for Redis Enterprise - BETA

This is a Beta version, we would like hear your feedback.
This is not meant for deployment in production environments.


## Use Case

## Prerequisites

In order to use this extension, you do need a [Standalone JAVA Machine Agent](https://docs.appdynamics.com/display/PRO44/Standalone+Machine+Agents) or [SIM Agent](https://docs.appdynamics.com/display/PRO44/Server+Visibility).  For more details on downloading these products, please  visit [here](https://download.appdynamics.com/).
The extension needs to be able to connect to Redis Enterprise in order to collect and send metrics. To do this, you will have to either establish a remote connection in between the extension and the product, or have an agent on the same machine running the product in order for the extension to collect and send the metrics.

## Installation

1. Download and unzip the RedisEnterpriseMonitor-1.0.0-Beta.zip to the "<MachineAgent_Dir>/monitors" directory
2. Edit the file config.yml as described below in Configuration Section, located in    <MachineAgent_Dir>/monitors/RedisEnterpriseMonitor and update the Redis Enterprise server(s) details.
3. All metrics to be reported are configured in metrics.xml. Users can remove entries from metrics.xml to stop the metric from reporting.
4. Restart the Machine Agent

Please place the extension in the **"monitors"** directory of your **Machine Agent** installation directory. Do not place the extension in the **"extensions"** directory of your **Machine Agent** installation directory.

In the AppDynamics Metric Browser, look for: Application Infrastructure Performance  | \<Tier\> | Custom Metrics | Redis Enterprise for default metric-path.

## Configuration
This section will help set up you config.yml
Note : Please make sure to not use tab (\t) while editing yaml files. You may want to validate the yaml file using a [yaml validator](http://yamllint.com/)

 1. Configure the `metricPrefix` according to you
 2. Configure the Redis Enterprise instances by specifying the name(required), host(required), port(required) and collectionName(required) of the Redis Enterprise instance, and rest of the fields (only if authentication enabled),
    encryptedPassword(only if password encryption required). If that is so, please update that name for the "applicationName" field. You can configure multiple instances as follows to report metrics
    For example,

    ```
    servers:
       # mandatory parameters
      - host: "localhost"
        port: 8983
        name: "Server 1"
        collectionName : ["gettingStarted","techproducts"]
        applicationName: "RedisEnterprise"


      - host: "localhost"
        port: 7574
        name: "Server 2"
        collectionName : ["gettingStarted","techproducts"]
        applicationName: "RedisEnterprise"

    ```
    3. Configure the encyptionKey for encryptionPasswords(only if password encryption required).
       For example,
    ```
       #Encryption key for Encrypted password.
       encryptionKey: "axcdde43535hdhdgfiniyy576"
    ```
    4. Configure the numberOfThreads
       For example,
       If number of servers that need to be monitored is 3, then number of threads required is 5 * 3 = 15
    ```
       numberOfThreads: 15
    ```

## Metrics


### Credentials Encryption

Please visit [this page](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

### Extensions Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following document on [How to use the Extensions WorkBench](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130)

### Troubleshooting
Please follow the steps listed in this [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension. If these don't solve your issue, please follow the last step on the [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) to contact the support team.

### Support Tickets
If after going through the [Troubleshooting Document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) you have not been able to get your extension working, please file a ticket and add the following information.

Please provide the following in order for us to assist you better.

   1. Stop the running machine agent.
   2. Delete all existing logs under <MachineAgent>/logs.
   3. Please enable debug logging by editing the file <MachineAgent>/conf/logging/log4j.xml. Change the level value of the following <logger> elements to debug.
       <logger name="com.singularity">
       <logger name="com.appdynamics">
   4. Start the machine agent and please let it run for 10 mins. Then zip and upload all the logs in the directory <MachineAgent>/logs/*.
   5. Attach the zipped <MachineAgent>/conf/* directory here.
   6. Attach the zipped <MachineAgent>/monitors/ExtensionFolderYouAreHavingIssuesWith directory here.

For any support related questions, you can also contact help@appdynamics.com.



### Contributing

Always feel free to fork and contribute any changes directly here on [GitHub](https://github.com/Appdynamics/RedisEnterprise-monitoring-extension/).

### Version
|          Name            |  Version   |
|--------------------------|------------|
|Extension Version         |1.0.0 Beta       |
|Product Tested On         ||
|Last Update               |08/16/2019|