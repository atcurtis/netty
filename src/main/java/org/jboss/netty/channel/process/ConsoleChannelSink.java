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

import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Executor;

import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.fireMessageReceived;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/8/14
 * Time: 4:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConsoleChannelSink extends AbstractChannelSink
{
  private final NioPipeChannelFactory factory;

  private ConsoleChannelSink(NioPipeChannelFactory factory)
  {
    this.factory = factory;
  }

  public ConsoleChannelSink(Executor workerExecutor)
  {
    this(new NioPipeChannelFactory(workerExecutor));
  }

  public ConsoleChannelSink(Executor workerExecutor,
                            int workerCount)
  {
    this(new NioPipeChannelFactory(workerExecutor, workerCount));
  }

  public ConsoleChannelSink(WorkerPool<NioWorker> workerPool)
  {
    this(new NioPipeChannelFactory(workerPool));
  }

  public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
      throws Exception
  {
    if (e instanceof ChannelStateEvent)
    {
      ChannelStateEvent event = (ChannelStateEvent) e;
      DefaultConsoleChannel channel = (DefaultConsoleChannel) event.getChannel();
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
          connect(channel, future, DefaultConsoleChannelFactory.PARENT);
        }
        else
        {
          channel.closeNow(future);
        }
        break;
      case INTEREST_OPS:
        int ops = (Integer) value;
        if (channel.getInputChannel() != null)
          Channels.setInterestOps(channel.getInputChannel(), ops & SelectionKey.OP_READ);
        if (channel.getOutputChannel() != null)
          Channels.setInterestOps(channel.getOutputChannel(), ops & SelectionKey.OP_WRITE);
        if (channel.getErrorChannel() != null)
          Channels.setInterestOps(channel.getErrorChannel(), ops & SelectionKey.OP_WRITE);
        break;
      }
    }
    else
    if (e instanceof MessageEvent)
    {
      final MessageEvent event = (MessageEvent) e;
      ConsoleChannel channel = (ConsoleChannel) event.getChannel();
      channel.getOutputChannel().write(event.getMessage()).addListener(new ChannelFutureListener()
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

  private void connect(DefaultConsoleChannel channel, ChannelFuture future, SocketAddress address)
  {
    DefaultChannelGroup group = new DefaultChannelGroup();
    try
    {
      factory.newConsole();

      for (NioPipeChannel.Type type = factory.getNextChannelType(); type != null; type = factory.getNextChannelType())
      {
        NioPipeChannel c;
        switch (type)
        {
        case CONSOLE_STDERR:
          c = factory.newChannel(Channels.pipeline());
          assert c.type() == type;
          Channels.connect(c, address);
          channel.stderr = (NioPipeSinkChannel) c;
          break;
        case CONSOLE_STDOUT:
          c = factory.newChannel(Channels.pipeline());
          assert c.type() == type;
          Channels.connect(c, address);
          channel.stdout = (NioPipeSinkChannel) c;
          break;
        case CONSOLE_STDIN:
          c = factory.newChannel(Channels.pipeline(new StdInHandler(channel)));
          assert c.type() == type;
          Channels.connect(c, address);
          channel.stdin = (NioPipeSourceChannel) c;
          break;
        default:
          throw new IllegalStateException();
        }
      }
      channel.localAddress = DefaultConsoleChannelFactory.LOCAL;
      channel.remoteAddress = address;
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

  private class StdInHandler implements ChannelUpstreamHandler
  {
    final ConsoleChannel consoleChannel;

    private StdInHandler(ConsoleChannel consoleChannel)
    {
      this.consoleChannel = consoleChannel;
    }

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
        throws Exception
    {
      if (e instanceof MessageEvent)
      {
        MessageEvent event = (MessageEvent) e;
        if (event.getMessage() instanceof ChannelBuffer)
        {
          fireMessageReceived(consoleChannel, event.getMessage(), consoleChannel.getRemoteAddress());
          e.getFuture().setSuccess();
          return;
        }
      }
      ctx.sendUpstream(e);
    }
  }

}
