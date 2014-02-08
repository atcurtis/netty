/*
 * Copyright 2014 Antony T Curtis, Xiphis
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.process;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 9:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ProcessAddress extends SocketAddress
{
  final ProcessBuilder builder;
  private volatile boolean fixated;
  private long handle;
  private Process process;

  private static final Field[] PROCESS_HANDLE = new Field[1];

  private static long getProcessHandle(Process process)
  {
    synchronized (PROCESS_HANDLE)
    {
      try
      {
        if (PROCESS_HANDLE[0] == null)
        {
          try
          {
            PROCESS_HANDLE[0] = process.getClass().getDeclaredField("handle");
          }
          catch (NoSuchFieldException ignored)
          {
            PROCESS_HANDLE[0] = process.getClass().getDeclaredField("pid");
          }
          PROCESS_HANDLE[0].setAccessible(true);
        }
        return ((Number) PROCESS_HANDLE[0].get(process)).longValue();
      }
      catch (IllegalAccessException e)
      {
        throw new RuntimeException(e);
      }
      catch (NoSuchFieldException e)
      {
        throw new RuntimeException(e);
      }
    }
  }

  ProcessAddress(ProcessAddress address, Process process)
  {
    builder = address.fixate().builder;
    fixated = true;
    handle = getProcessHandle(process);
    this.process = process;
  }

  private ProcessAddress(ProcessBuilder processBuilder)
  {
    builder = processBuilder;
    builder.redirectError(ProcessBuilder.Redirect.INHERIT);
  }

  public ProcessAddress(String... command)
  {
    this(new ProcessBuilder(command));
  }

  public ProcessAddress(List<String> command)
  {
    this(new ProcessBuilder(command));
  }

  Process process()
  {
    return process;
  }

  long handle()
  {
    return handle;
  }

  public List<String> command()
  {
    if (fixated)
      return Collections.unmodifiableList(builder.command());
    else
      return builder.command();
  }

  public ProcessAddress directory(File directory)
  {
    if (fixated)
      throw new UnsupportedOperationException("fixated");
    builder.directory(directory);
    return this;
  }

  public File directory()
  {
    return builder.directory();
  }

  public Map<String,String> environment()
  {
    if (fixated)
      return Collections.unmodifiableMap(builder.environment());
    else
      return builder.environment();
  }

  public ProcessAddress redirectInput(ProcessBuilder.Redirect redirect)
  {
    if (fixated)
      throw new UnsupportedOperationException("fixated");
    builder.redirectInput(redirect);
    return this;
  }

  public ProcessAddress redirectInput(File file)
  {
    if (fixated)
      throw new UnsupportedOperationException("fixated");
    builder.redirectInput(file);
    return this;
  }

  public ProcessBuilder.Redirect redirectInput()
  {
    return builder.redirectInput();
  }

  public ProcessAddress redirectOutput(ProcessBuilder.Redirect redirect)
  {
    if (fixated)
      throw new UnsupportedOperationException("fixated");
    builder.redirectOutput(redirect);
    return this;
  }

  public ProcessAddress redirectOutput(File file)
  {
    if (fixated)
      throw new UnsupportedOperationException("fixated");
    builder.redirectOutput(file);
    return this;
  }


  public ProcessBuilder.Redirect redirectOutput()
  {
    return builder.redirectOutput();
  }

  public ProcessAddress redirectError(ProcessBuilder.Redirect redirect)
  {
    if (fixated)
      throw new UnsupportedOperationException("fixated");
    builder.redirectError(redirect);
    return this;
  }

  public ProcessAddress redirectError(File file)
  {
    if (fixated)
      throw new UnsupportedOperationException("fixated");
    builder.redirectError(file);
    return this;
  }

  public ProcessBuilder.Redirect redirectError()
  {
    return builder.redirectError();
  }

  public ProcessAddress redirectErrorStream(boolean redirect)
  {
    if (fixated)
      throw new UnsupportedOperationException("fixated");
    builder.redirectErrorStream(redirect);
    return this;
  }

  public boolean redirectErrorStream() {
    return builder.redirectErrorStream();
  }

  public ProcessAddress fixate()
  {
    fixated = true;
    return this;
  }

  public Process start()
      throws IOException
  {
    return builder.start();
  }
}
