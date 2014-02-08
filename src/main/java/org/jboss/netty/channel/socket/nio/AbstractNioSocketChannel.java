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

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;

import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 10:39 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractNioSocketChannel<C extends SelectableChannel> extends AbstractNioChannel<C>
{
  private volatile InetSocketAddress localAddress;
  volatile InetSocketAddress remoteAddress;

  protected AbstractNioSocketChannel(Integer id, Channel parent, ChannelFactory factory, ChannelPipeline pipeline,
                                     ChannelSink sink, AbstractNioWorker worker, C ch)
  {
    super(id, parent, factory, pipeline, sink, worker, ch);
  }

  protected AbstractNioSocketChannel(Channel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink,
                                     AbstractNioWorker worker, C ch)
  {
    super(parent, factory, pipeline, sink, worker, ch);
  }

  public InetSocketAddress getLocalAddress() {
    InetSocketAddress localAddress = this.localAddress;
    if (localAddress == null) {
      try {
        localAddress = getLocalSocketAddress();
        if (localAddress.getAddress().isAnyLocalAddress()) {
          // Don't cache on a wildcard address so the correct one
          // will be cached once the channel is connected/bound
          return localAddress;
        }
        this.localAddress = localAddress;
      } catch (Throwable t) {
        // Sometimes fails on a closed socket in Windows.
        return null;
      }
    }
    return localAddress;
  }

  public InetSocketAddress getRemoteAddress() {
    InetSocketAddress remoteAddress = this.remoteAddress;
    if (remoteAddress == null) {
      try {
        this.remoteAddress = remoteAddress =
            getRemoteSocketAddress();
      } catch (Throwable t) {
        // Sometimes fails on a closed socket in Windows.
        return null;
      }
    }
    return remoteAddress;
  }

  abstract InetSocketAddress getLocalSocketAddress() throws Exception;

  abstract InetSocketAddress getRemoteSocketAddress() throws Exception;

}
