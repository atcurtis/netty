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
package org.jboss.netty.example.process;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.process.DefaultProcessChannelFactory;
import org.jboss.netty.channel.process.ProcessAddress;

import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/8/14
 * Time: 3:03 AM
 * To change this template use File | Settings | File Templates.
 */
public class ProcessExample
{
  public void run() {
    // Configure the client.
    ClientBootstrap bootstrap = new ClientBootstrap(
        new DefaultProcessChannelFactory(
            Executors.newCachedThreadPool()));

    // Set up the pipeline factory.
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(new PrintHandler());
      }
    });

    // Start the connection attempt.
    ChannelFuture future = bootstrap.connect(new ProcessAddress("/usr/bin/find","/"));

    // Wait until the connection is closed or the connection attempt fails.
    future.getChannel().getCloseFuture().awaitUninterruptibly();

    // Shut down thread pools to exit.
    bootstrap.releaseExternalResources();
  }

  public static void main(String[] args) throws Exception {
    new ProcessExample().run();
  }
}
