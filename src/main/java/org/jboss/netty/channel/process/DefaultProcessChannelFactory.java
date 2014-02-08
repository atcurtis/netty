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

import java.util.concurrent.Executor;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 10:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultProcessChannelFactory implements ProcessChannelFactory
{
  private final ProcessChannelSink sink;

  private DefaultProcessChannelFactory(ProcessChannelSink sink)
  {
    this.sink = sink;
  }

  public DefaultProcessChannelFactory(Executor workerExecutor)
  {
    this(new ProcessChannelSink(workerExecutor));
  }

  public DefaultProcessChannelFactory(Executor workerExecutor,
                                      int workerCount)
  {
    this(new ProcessChannelSink(workerExecutor, workerCount));
  }

  public DefaultProcessChannelFactory(WorkerPool<NioWorker> workerPool)
  {
    this(new ProcessChannelSink(workerPool));
  }

  public ProcessChannel newChannel(ChannelPipeline pipeline)
  {
    return new DefaultProcessChannel(null, this, pipeline, sink);
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
