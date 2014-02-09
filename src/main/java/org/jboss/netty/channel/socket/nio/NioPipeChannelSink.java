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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;

import java.io.IOException;
import java.net.SocketAddress;

import static org.jboss.netty.channel.Channels.fireExceptionCaught;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 11:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class NioPipeChannelSink extends AbstractNioChannelSink
{
  private final SocketAddress LOCAL = new SocketAddress()
  {
    @Override
    public String toString()
    {
      return "LOCAL";
    }
  };

  public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
      throws Exception
  {
    if (e instanceof ChannelStateEvent)
    {
      ChannelStateEvent event = (ChannelStateEvent) e;
      NioPipeChannel channel = (NioPipeChannel) event.getChannel();
      ChannelFuture future = event.getFuture();
      ChannelState state = event.getState();
      Object value = event.getValue();

      switch (state)
      {
      case OPEN:
        if (Boolean.FALSE.equals(value))
        {
          channel.worker.close(channel, future);
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
          channel.worker.close(channel, future);
        }
        break;
      case CONNECTED:
        if (value != null)
        {
          try
          {
            channel.channel.configureBlocking(false);
            channel.localAddress = LOCAL;
            channel.remoteAddress = (SocketAddress) value;
            channel.worker.register(channel, future);
          }
          catch (IOException ex)
          {
            future.setFailure(ex);
            fireExceptionCaught(channel, ex);
          }
        }
        else
        {
          channel.worker.close(channel, future);
        }
        break;
      case INTEREST_OPS:
        channel.worker.setInterestOps(channel, future, ((Integer) value));
        break;
      }
    } else
    if (e instanceof MessageEvent)
    {
      MessageEvent event = (MessageEvent) e;
      NioPipeChannel channel = (NioPipeChannel) event.getChannel();
      boolean offered = channel.writeBufferQueue.offer(event);
      assert offered;
      channel.worker.writeFromUserCode(channel);
    }
  }
}
