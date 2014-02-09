package org.jboss.netty.example.console;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.process.DefaultConsoleChannelFactory;

import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/8/14
 * Time: 7:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConsoleTest
{
  public void run() {
    // Configure the client.
    ClientBootstrap bootstrap = new ClientBootstrap(
        new DefaultConsoleChannelFactory(
            Executors.newCachedThreadPool()));

    // Set up the pipeline factory.
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline();
      }
    });

    // Start the connection attempt.
    ChannelFuture future = bootstrap.connect(DefaultConsoleChannelFactory.PARENT);

    future.addListener(new ChannelFutureListener()
    {
      public void operationComplete(ChannelFuture future)
          throws Exception
      {
        if (future.isSuccess())
        {
          Channels.write(future.getChannel(), ChannelBuffers.wrappedBuffer("Hello World\n".getBytes()))
              .addListener(new ChannelFutureListener()
              {
                public void operationComplete(ChannelFuture future)
                    throws Exception
                {
                  Channels.close(future.getChannel());
                }
              });
        }
      }
    });

    // Wait until the connection is closed or the connection attempt fails.
    future.getChannel().getCloseFuture().awaitUninterruptibly();

    // Shut down thread pools to exit.
    bootstrap.releaseExternalResources();
  }

  public static void main(String[] args) throws Exception {
    new ConsoleTest().run();
  }

}
