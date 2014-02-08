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
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.nio.NioPipeSinkChannel;
import org.jboss.netty.channel.socket.nio.NioPipeSourceChannel;

import java.net.SocketAddress;

import static org.jboss.netty.channel.Channels.fireChannelClosed;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 10:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultProcessChannel extends AbstractChannel implements ProcessChannel
{
  private final ChannelConfig config;
  volatile ChannelGroup group;
  volatile NioPipeSinkChannel stdin;
  volatile NioPipeSourceChannel stdout;
  volatile NioPipeSourceChannel stderr;
  volatile ProcessAddress remoteAddress;

  protected DefaultProcessChannel(Channel parent, ChannelFactory factory, ChannelPipeline pipeline, ProcessChannelSink sink)
  {
    super(parent, factory, pipeline, sink);
    config = new DefaultChannelConfig();
  }

  public ChannelConfig getConfig()
  {
    return config;
  }

  public boolean isBound()
  {
    return getRemoteAddress() != null;
  }

  public boolean isConnected()
  {
    ProcessAddress address = getRemoteAddress();
    if (address == null || address.process() == null)
      return false;
    try
    {
      address.process().exitValue();
      return false;
    }
    catch (IllegalThreadStateException ignored)
    {
      return true;
    }
  }

  public SocketAddress getLocalAddress()
  {
    return null;
  }

  public ProcessAddress getRemoteAddress()
  {
    return remoteAddress;
  }

  public NioPipeSinkChannel getInputChannel()
  {
    return stdin;
  }

  public NioPipeSourceChannel getOutputChannel()
  {
    return stdout;
  }

  public NioPipeSourceChannel getErrorChannel()
  {
    return stderr;
  }

  void closeNow(ChannelFuture future)
  {
    ProcessAddress address = this.remoteAddress;
    try {
      // Close the self.
      if (!setClosed()) {
        return;
      }

      if (address.process() != null)
        address.process().destroy();

      if (group != null)
      {
        group.close();
        group = null;
      }

      fireChannelClosed(this);
    }
    finally
    {
      future.setSuccess();
    }
  }
}
