/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.process;

import com.google.common.io.Closeables;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ProcessWrapper {

  private final static Logger LOGGER = LoggerFactory.getLogger(ProcessWrapper.class);

  final int port;
  final String name;
  final String workDir;
  final String className;
  final String[] classPath;
  final Map<String, String> properties;

  ProcessMXBean processMXBean;
  MBeanServerConnection mBeanServer;

  final java.lang.Process process;
  private StreamGobbler errorGobbler;
  private StreamGobbler outputGobbler;


  private ScheduledFuture<?> pingTask = null;
  final ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
  final Runnable pinging = new Runnable() {
    public void run() {
      processMXBean.ping();
    }
  };



  public ProcessWrapper(String workDir, String className, Map<String, String> properties, final String name, String... classPath) {
    this.port = NetworkUtils.freePort();
    LOGGER.info("Creating Process for '{}' with workDir: '{}' and monitoring port: {}", name, workDir, port);
    this.workDir = workDir;
    this.name = name;
    this.className = className;
    this.classPath = classPath;
    this.properties = properties;

    this.process = executeProcess();

    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          process.waitFor();
        } catch (InterruptedException e) {
          e.printStackTrace();
        } finally {
          waitUntilFinish(outputGobbler);
          waitUntilFinish(errorGobbler);
        }
        LOGGER.warn("Process '{}' Unexpectedly finished. Node should shutdown.", name);
      }
    }).start();

    // Waiting for the Child VM to start and for JMX to be available
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    JMXServiceURL jmxUrl = null;

    try {
      String protocol = "rmi";
      String path = "/jndi/rmi://" + InetAddress.getLocalHost().getHostName() + ":" + port + "/jmxrmi";
      jmxUrl = new JMXServiceURL(protocol, InetAddress.getLocalHost().getHostName(), port, path);
      JMXConnector jmxConnector = JMXConnectorFactory.connect(jmxUrl, null);
      mBeanServer = jmxConnector.getMBeanServerConnection();
      processMXBean = JMX.newMBeanProxy(mBeanServer, Process.objectNameFor(name), ProcessMXBean.class);
    } catch (MalformedURLException e) {
      throw new IllegalStateException("JMXUrl '" + jmxUrl + "'is not valid", e);
    } catch (UnknownHostException e) {
      throw new IllegalStateException("Could not get hostname", e);
    } catch (IOException e) {
      throw new IllegalStateException("Could not connect to JMX service", e);
    }

    pingTask = monitor.scheduleWithFixedDelay(pinging, 0, 3, TimeUnit.SECONDS);
  }

  public boolean isReady() {
    return processMXBean.isReady();
  }

  public void stop() {
    pingTask.cancel(true);
    processMXBean.stop();
  }

  public String getName() {
    return name;
  }

  public java.lang.Process executeProcess() {
    ProcessBuilder processBuilder =
      new ProcessBuilder("java",
        "-Dcom.sun.management.jmxremote",
        "-Dcom.sun.management.jmxremote.port=" + port,
        "-Dcom.sun.management.jmxremote.authenticate=false",
        "-Dcom.sun.management.jmxremote.ssl=false",
        "-cp",
        StringUtils.join(classPath, ":"),
        className);
    processBuilder.environment().putAll(properties);
    processBuilder.environment().put(Process.NAME_PROPERTY, this.getName());
    processBuilder.environment().put(Process.PORT_PROPERTY, Integer.toString(port));

    //check that working directory exists.
    File workDirectory = new File(workDir);
    if(!workDirectory.exists()) {
      throw new IllegalStateException("Work directory does not exist.");
    } else {
      processBuilder.directory(FileUtils.getFile(workDir));
    }

    try {
      java.lang.Process process = processBuilder.start();
      errorGobbler = new StreamGobbler(process.getErrorStream(), this.getName() + "-ERROR");
      outputGobbler = new StreamGobbler(process.getInputStream(), this.getName());
      outputGobbler.start();
      errorGobbler.start();
      return process;
    } catch (IOException e) {
      throw new IllegalStateException("Io Exception in ProcessWrapper", e);
    }
  }


  private void closeStreams(java.lang.Process process) {
    if (process != null) {
      Closeables.closeQuietly(process.getInputStream());
      Closeables.closeQuietly(process.getOutputStream());
      Closeables.closeQuietly(process.getErrorStream());
    }
  }

  private void waitUntilFinish(StreamGobbler thread) {
    if (thread != null) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        LOGGER.error("InterruptedException while waiting finish of " + thread.toString(), e);
      }
    }
  }

  private static class StreamGobbler extends Thread {
    private final InputStream is;
    private volatile Exception exception;
    private final String pName;

    StreamGobbler(InputStream is, String name) {
      super("ProcessStreamGobbler");
      this.is = is;
      this.pName = name;
    }

    @Override
    public void run() {
      InputStreamReader isr = new InputStreamReader(is);
      BufferedReader br = new BufferedReader(isr);
      try {
        String line;
        while ((line = br.readLine()) != null) {
          LOGGER.info(pName + " > " + line);
        }
      } catch (IOException ioe) {
        exception = ioe;

      } finally {
        Closeables.closeQuietly(br);
        Closeables.closeQuietly(isr);
      }
    }

    public Exception getException() {
      return exception;
    }
  }
}