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

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.WorkerPool;

import java.net.SocketAddress;
import java.util.concurrent.Executor;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/8/14
 * Time: 4:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultConsoleChannelFactory implements ConsoleChannelFactory
{
  public static final SocketAddress LOCAL = new SocketAddress()
  {
    @Override
    public String toString()
    {
      return "LOCAL";
    }
  };
  public static final SocketAddress PARENT = new SocketAddress()
  {
    @Override
    public String toString()
    {
      return "PARENT";
    }
  };

  private final ConsoleChannelSink sink;

  private DefaultConsoleChannelFactory(ConsoleChannelSink sink)
  {
    this.sink = sink;
  }

  public DefaultConsoleChannelFactory(Executor workerExecutor)
  {
    this(new ConsoleChannelSink(workerExecutor));
  }

  public DefaultConsoleChannelFactory(Executor workerExecutor,
                                      int workerCount)
  {
    this(new ConsoleChannelSink(workerExecutor, workerCount));
  }

  public DefaultConsoleChannelFactory(WorkerPool<NioWorker> workerPool)
  {
    this(new ConsoleChannelSink(workerPool));
  }


  public ConsoleChannel newChannel(ChannelPipeline pipeline)
  {
    return new DefaultConsoleChannel(null, this, pipeline, sink);
  }

  public void shutdown()
  {
    sink.shutdown();
  }

  public void releaseExternalResources()
  {
    sink.releaseExternalResources();
  }
}
