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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioPipeChannel;
import org.jboss.netty.channel.socket.nio.NioPipeChannelFactory;
import org.jboss.netty.channel.socket.nio.NioPipeSinkChannel;
import org.jboss.netty.channel.socket.nio.NioPipeSourceChannel;
import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.WorkerPool;

import java.nio.channels.SelectionKey;
import java.util.concurrent.Executor;

import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.fireMessageReceived;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 10:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class ProcessChannelSink extends AbstractChannelSink
{
  private final NioPipeChannelFactory factory;

  private ProcessChannelSink(NioPipeChannelFactory factory)
  {
    this.factory = factory;
  }

  public ProcessChannelSink(Executor workerExecutor)
  {
    this(new NioPipeChannelFactory(workerExecutor));
  }

  public ProcessChannelSink(Executor workerExecutor,
                            int workerCount)
  {
    this(new NioPipeChannelFactory(workerExecutor, workerCount));
  }

  public ProcessChannelSink(WorkerPool<NioWorker> workerPool)
  {
    this(new NioPipeChannelFactory(workerPool));
  }

  public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
      throws Exception
  {
    if (e instanceof ChannelStateEvent)
    {
      ChannelStateEvent event = (ChannelStateEvent) e;
      DefaultProcessChannel channel = (DefaultProcessChannel) event.getChannel();
      ChannelFuture future = event.getFuture();
      ChannelState state = event.getState();
      Object value = event.getValue();

      switch (state)
      {
      case OPEN:
        if (Boolean.FALSE.equals(value))
        {
          channel.closeNow(future);
        }
        break;
      case BOUND:
        if (value != null)
        {
          Throwable t = new UnsupportedOperationException("bind");
          future.setFailure(t);
          fireExceptionCaught(channel, t);
        }
        else
        {
          channel.closeNow(future);
        }
        break;
      case CONNECTED:
        if (value != null)
        {
          connect(channel, future, (ProcessAddress) value);
        }
        else
        {
          channel.closeNow(future);
        }
        break;
      case INTEREST_OPS:
        int ops = (Integer) value;
        if (channel.getInputChannel() != null)
          Channels.setInterestOps(channel.getInputChannel(), ops & SelectionKey.OP_WRITE);
        if (channel.getOutputChannel() != null)
          Channels.setInterestOps(channel.getOutputChannel(), ops & SelectionKey.OP_READ);
        if (channel.getErrorChannel() != null)
          Channels.setInterestOps(channel.getErrorChannel(), ops & SelectionKey.OP_READ);
        break;
      }
    } else
    if (e instanceof MessageEvent)
    {
      final MessageEvent event = (MessageEvent) e;
      ProcessChannel channel = (ProcessChannel) event.getChannel();
      channel.getInputChannel().write(event.getMessage()).addListener(new ChannelFutureListener()
      {
        public void operationComplete(ChannelFuture future)
            throws Exception
        {
          if (future.isSuccess())
          {
            event.getFuture().setSuccess();
          }
          else
          {
            event.getFuture().setFailure(future.getCause());
          }
        }
      });
    }
  }

  private void connect(DefaultProcessChannel channel, ChannelFuture future, ProcessAddress address)
  {
    DefaultChannelGroup group = new DefaultChannelGroup();
    try
    {
      Process process = factory.newProcess(address);
      address = new ProcessAddress(address, process);

      for (NioPipeChannel.Type type = factory.getNextChannelType(); type != null; type = factory.getNextChannelType())
      {
        NioPipeChannel c;
        switch (type)
        {
        case PROCESS_STDERR:
          c = factory.newChannel(Channels.pipeline(new StdOutHandler(channel)));
          assert c.type() == type;
          group.add(c);
          Channels.connect(c, address);
          channel.stderr = (NioPipeSourceChannel) c;
          break;
        case PROCESS_STDOUT:
          c = factory.newChannel(Channels.pipeline(new StdOutHandler(channel)));
          assert c.type() == type;
          group.add(c);
          Channels.connect(c, address);
          channel.stdout = (NioPipeSourceChannel) c;
          break;
        case PROCESS_STDIN:
          c = factory.newChannel(Channels.pipeline());
          assert c.type() == type;
          group.add(c);
          Channels.connect(c, address);
          channel.stdin = (NioPipeSinkChannel) c;
          break;
        default:
          throw new IllegalStateException();
        }
      }
      channel.remoteAddress = address;
      channel.group = group;
      fireChannelConnected(channel, channel.remoteAddress);
      future.setSuccess();
    }
    catch (Throwable e)
    {
      group.close();
      future.setFailure(e);
      fireExceptionCaught(channel, e);
    }
  }

  public void shutdown()
  {
    factory.shutdown();
  }

  public void releaseExternalResources()
  {
    factory.releaseExternalResources();
  }

  private class StdOutHandler implements ChannelUpstreamHandler
  {
    final ProcessChannel processChannel;

    private StdOutHandler(ProcessChannel processChannel)
    {
      this.processChannel = processChannel;
    }

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
        throws Exception
    {
      if (e instanceof MessageEvent)
      {
        MessageEvent event = (MessageEvent) e;
        if (event.getMessage() instanceof ChannelBuffer)
        {
          fireMessageReceived(processChannel, event.getMessage(), processChannel.getRemoteAddress());
          e.getFuture().setSuccess();
          return;
        }
      }
      ctx.sendUpstream(e);
    }
  }
}
