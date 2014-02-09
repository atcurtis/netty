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

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;

import java.net.SocketAddress;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;

import static org.jboss.netty.channel.Channels.fireChannelOpen;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 10:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class NioPipeSourceChannel extends NioPipeChannel<Pipe.SourceChannel>
{
  NioPipeSourceChannel(
      Pipe.SourceChannel pipeSource,
      ChannelFactory factory, ChannelPipeline pipeline,
      NioPipeChannelSink sink, NioWorker worker, Type type) {

    super(null, factory, pipeline, sink, worker, pipeSource, type);
    setInterestOpsNow(SelectionKey.OP_READ);
    fireChannelOpen(this);
  }

  @Override
  public ChannelFuture write(Object message, SocketAddress remoteAddress) {
    return getUnsupportedOperationFuture();
  }
}
