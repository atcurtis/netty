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

import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 10:54 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class NioPipeChannel<C extends SelectableChannel> extends AbstractNioChannel<C>
{
  private static final int ST_OPEN = 0;
  private static final int ST_BOUND = 1;
  private static final int ST_CONNECTED = 2;
  private static final int ST_CLOSED = -1;
  @SuppressWarnings("RedundantFieldInitialization")
  volatile int state = ST_OPEN;
  volatile SocketAddress remoteAddress;
  volatile SocketAddress localAddress;

  public enum Type
  {
    PIPE_SOURCE,
    PIPE_SINK,
    PROCESS_STDIN,
    PROCESS_STDOUT,
    PROCESS_STDERR,
    CONSOLE_STDIN,
    CONSOLE_STDOUT,
    CONSOLE_STDERR
  }

  static <T extends SelectableChannel> NioPipeChannel<T> makeChannel(ChannelFactory factory, ChannelPipeline pipeline, NioPipeChannelSink sink,
      AbstractNioWorker worker, T channel, Type type)
  {
    return new NioPipeChannel<T>(null, factory, pipeline, sink, worker, channel, type)
    {
    };
  }

  private final NioPipeChannelConfig config;
  private final Type type;

  protected NioPipeChannel(Channel parent, ChannelFactory factory, ChannelPipeline pipeline, NioPipeChannelSink sink,
                           AbstractNioWorker worker, C ch, Type type)
  {
    super(parent, factory, pipeline, sink, worker, ch);
    config = new DefaultNioPipeChannelConfig();
    this.type = type;
  }

  public Type type()
  {
    return type;
  }

  @Override
  public NioWorker getWorker() {
    return (NioWorker) super.getWorker();
  }

  @Override
  public NioPipeChannelConfig getConfig() {
    return config;
  }

  @Override
  public boolean isOpen() {
    return state >= ST_OPEN;
  }

  public boolean isBound() {
    return state >= ST_BOUND;
  }

  public boolean isConnected() {
    return state == ST_CONNECTED;
  }

  final void setBound() {
    assert state == ST_OPEN : "Invalid state: " + state;
    state = ST_BOUND;
  }

  final void setConnected() {
    if (state != ST_CLOSED) {
      state = ST_CONNECTED;
    }
  }

  @Override
  protected boolean setClosed() {
    if (super.setClosed()) {
      state = ST_CLOSED;
      return true;
    }
    return false;
  }

  public SocketAddress getLocalAddress()
  {
    return localAddress;
  }

  public SocketAddress getRemoteAddress()
  {
    return remoteAddress;
  }
}
