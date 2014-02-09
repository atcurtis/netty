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

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.socket.nio.NioPipeSinkChannel;
import org.jboss.netty.channel.socket.nio.NioPipeSourceChannel;

import java.net.SocketAddress;

import static org.jboss.netty.channel.Channels.fireChannelClosed;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/8/14
 * Time: 4:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultConsoleChannel extends AbstractChannel implements ConsoleChannel
{
  private final ChannelConfig config;
  volatile NioPipeSourceChannel stdin;
  volatile NioPipeSinkChannel stdout;
  volatile NioPipeSinkChannel stderr;
  volatile SocketAddress localAddress;
  volatile SocketAddress remoteAddress;

  protected DefaultConsoleChannel(Channel parent, ChannelFactory factory, ChannelPipeline pipeline, ConsoleChannelSink sink)
  {
    super(parent, factory, pipeline, sink);
    config = new DefaultChannelConfig();
  }

  public NioPipeSourceChannel getInputChannel()
  {
    return stdin;
  }

  public NioPipeSinkChannel getOutputChannel()
  {
    return stdout;
  }

  public NioPipeSinkChannel getErrorChannel()
  {
    return stderr;
  }

  public ChannelConfig getConfig()
  {
    return config;
  }

  public boolean isBound()
  {
    return remoteAddress != null;
  }

  public boolean isConnected()
  {
    return remoteAddress != null;
  }

  public SocketAddress getLocalAddress()
  {
    return localAddress;
  }

  public SocketAddress getRemoteAddress()
  {
    return remoteAddress;
  }

  void closeNow(ChannelFuture future)
  {
    SocketAddress address = this.remoteAddress;
    try {
      // Close the self.
      if (!setClosed()) {
        return;
      }

      fireChannelClosed(this);
    }
    finally
    {
      future.setSuccess();
    }
  }

}
