Disk Usage Plugin
=========================

[![Jenkins Plugins](https://img.shields.io/jenkins/plugin/v/disk-usage)](https://github.com/jenkinsci/disk-usage-plugin/releases)
[![Jenkins Plugin installs](https://img.shields.io/jenkins/plugin/i/disk-usage)](https://plugins.jenkins.io/disk-usage)
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/disk-usage-plugin/master)](https://ci.jenkins.io/blue/organizations/jenkins/Plugins%2Fdisk-usage-plugin/branches)
[![javadoc](https://img.shields.io/badge/javadoc-available-brightgreen.svg)](https://javadoc.jenkins.io/plugin/disk-usage/)

This plugin records disk usage.

# Configuration

Showing disk usage trend graph is optional - unselect the `Show disk usage trend graph` checkbox on the global configuration page (`Manage Jenkins` -> `System configuration`) if you don't want to see the graph on the project page.

# Usage

When you install this plugin, disk usage is calculated every 6 hours. You can see project list with occupied disk space by going to the "Disk Usage" page in the management section (`Manage Jenkins` -> `Disk Usage`). The same page also allows you to schedule disk usage calculation immediately.
  
![](docs/images/du-overview.png)

More detailed information can be seen on the project page, where you can find disk usage for each build and workspace, as well as a graph with disk usage trend.
  
![](docs/images/du-project.png)

You can configure the plugin in `Manage Jenkins` -> `Configure System`

![](docs/images/du-configuration.png)
