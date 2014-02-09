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
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.process.ProcessAddress;
import org.jboss.netty.util.ExternalResourceReleasable;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 11:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class NioPipeChannelFactory implements ChannelFactory
{
  private final WorkerPool<NioWorker> workerPool;
  private final NioPipeChannelSink sink;
  private boolean releasePools;
  private final Queue<Factory> factoryQueue = new LinkedList<Factory>();

  /**
   * Creates a new {@link NioClientSocketChannelFactory} which uses {@link java.util.concurrent.Executors#newCachedThreadPool()}
   * for the worker and boss executors.
   *
   * See {@link #NioPipeChannelFactory(java.util.concurrent.Executor)}
   */
  public NioPipeChannelFactory()
  {
    this(Executors.newCachedThreadPool());
    releasePools = true;
  }

  /**
   * Creates a new instance.  Calling this constructor is same with calling
   * {@link #NioPipeChannelFactory(java.util.concurrent.Executor, int)} with
   * 1 and (2 * the number of available processors in the machine) for
   * <tt>bossCount</tt> and <tt>workerCount</tt> respectively.  The number of
   * available processors is obtained by {@link Runtime#availableProcessors()}.
   *
   * @param workerExecutor
   *        the {@link java.util.concurrent.Executor} which will execute the worker threads
   */
  public NioPipeChannelFactory(Executor workerExecutor)
  {
    this(workerExecutor, SelectorUtil.DEFAULT_IO_THREADS);
  }

  /**
   * Creates a new instance.
   *
   * @param workerExecutor
   *        the {@link Executor} which will execute the worker threads
   * @param workerCount
   *        the maximum number of I/O worker threads
   */
  public NioPipeChannelFactory(
      Executor workerExecutor,
      int workerCount)
  {
    this(new NioWorkerPool(workerExecutor, workerCount));
  }

  /**
   * Creates a new instance.
   *
   * @param workerPool
   *        the {@link WorkerPool} to use to do the IO
   */
  public NioPipeChannelFactory(WorkerPool<NioWorker> workerPool)
  {
    if (workerPool == null)
    {
      throw new NullPointerException("workerPool");
    }
    this.workerPool = workerPool;
    sink = new NioPipeChannelSink();
  }

  public NioPipeChannel.Type getNextChannelType()
  {
    Factory factory = factoryQueue.peek();
    return factory != null ? factory.type() : null;
  }

  public void newPipe()
      throws IOException
  {
    Pipe pipe = Pipe.open();
    factoryQueue.offer(new PipeSink(pipe));
    factoryQueue.offer(new PipeSource(pipe));
  }

  public void newConsole()
  {
    factoryQueue.offer(new ConsoleInput());
    factoryQueue.offer(new ConsoleOutput());
    factoryQueue.offer(new ConsoleError());
  }

  public Process newProcess(ProcessAddress address)
      throws IOException
  {
    address.fixate();
    Process process = address.start();
    if (address.redirectInput().type() == ProcessBuilder.Redirect.Type.PIPE)
    {
      factoryQueue.offer(new ProcessInput(process));
    }
    if (address.redirectOutput().type() == ProcessBuilder.Redirect.Type.PIPE)
    {
      factoryQueue.offer(new ProcessOutput(process));
    }
    if (address.redirectError().type() == ProcessBuilder.Redirect.Type.PIPE)
    {
      factoryQueue.offer(new ProcessError(process));
    }
    return process;
  }

  public NioPipeChannel newChannel(ChannelPipeline pipeline)
  {
    return factoryQueue.remove().newChannel(this, pipeline, sink, workerPool.nextWorker());
  }

  public void shutdown()
  {
    workerPool.shutdown();
    if (releasePools)
    {
      releasePools();
    }
  }

  public void releaseExternalResources()
  {
    shutdown();
    releasePools();
  }

  private void releasePools()
  {
    if (workerPool instanceof ExternalResourceReleasable)
    {
      ((ExternalResourceReleasable) workerPool).releaseExternalResources();
    }
  }

  private interface Factory
  {
    NioPipeChannel newChannel(
        ChannelFactory factory, ChannelPipeline pipeline,
        NioPipeChannelSink sink, NioWorker worker);

    NioPipeChannel.Type type();
  }

  private static class PipeSource implements Factory
  {
    private final Pipe pipe;

    private PipeSource(Pipe pipe)
    {
      this.pipe = pipe;
    }

    public NioPipeSourceChannel newChannel(ChannelFactory factory,
                                           ChannelPipeline pipeline,
                                           NioPipeChannelSink sink,
                                           NioWorker worker)
    {
      return new NioPipeSourceChannel(pipe.source(), factory, pipeline, sink, worker, type());
    }

    public NioPipeChannel.Type type()
    {
      return NioPipeChannel.Type.PIPE_SOURCE;
    }
  }

  private static class PipeSink implements Factory
  {
    private final Pipe pipe;

    private PipeSink(Pipe pipe)
    {
      this.pipe = pipe;
    }

    public NioPipeSinkChannel newChannel(ChannelFactory factory,
                                         ChannelPipeline pipeline,
                                         NioPipeChannelSink sink,
                                         NioWorker worker)
    {
      return new NioPipeSinkChannel(pipe.sink(), factory, pipeline, sink, worker, type());
    }

    public NioPipeChannel.Type type()
    {
      return NioPipeChannel.Type.PIPE_SINK;
    }
  }

  private static class ProcessInput implements Factory
  {
    private final Process process;

    public ProcessInput(Process process)
    {
      this.process = process;
    }

    public NioPipeChannel newChannel(ChannelFactory factory, ChannelPipeline pipeline, NioPipeChannelSink sink,
                                     NioWorker worker)
    {
      OutputStream s = process.getOutputStream();
      while (s instanceof FilterOutputStream)
      {
        s = unwrap((FilterOutputStream) s);
      }
      try
      {
        FileDescriptor fd;
        if (s instanceof FileOutputStream)
          fd = ((FileOutputStream) s).getFD();
        else
          fd = new FileOutputStream("/dev/null", true).getFD();
        SelectorProvider provider = SelectorProvider.provider();
        return new NioPipeSinkChannel(newInstance(SINK_CHANNEL_CONSTRUCTOR, provider, fd),
                                      factory, pipeline, sink, worker, type());
      }
      catch (IOException e)
      {
        throw new RuntimeException(e);
      }
    }

    public NioPipeChannel.Type type()
    {
      return NioPipeChannel.Type.PROCESS_STDIN;
    }
  }

  private static abstract class AbstractProcessOutput implements Factory
  {
    protected final Process process;

    protected AbstractProcessOutput(Process process)
    {
      this.process = process;
    }

    protected abstract InputStream getInputStream();

    public final NioPipeSourceChannel newChannel(ChannelFactory factory, ChannelPipeline pipeline,
                                                 NioPipeChannelSink sink, final NioWorker worker)
    {
      InputStream s = getInputStream();
      while (s instanceof FilterInputStream)
      {
        s = unwrap((FilterInputStream) s);
      }
      try
      {
        if (s instanceof FileInputStream)
        {
          FileDescriptor fd = ((FileInputStream) s).getFD();
          SelectorProvider provider = SelectorProvider.provider();
          return new NioPipeSourceChannel(newInstance(SOURCE_CHANNEL_CONSTRUCTOR, provider, fd),
                                          factory, pipeline, sink, worker, type());
        }
        else
        {
          final ReadableByteChannel input = Channels.newChannel(s);
          final Pipe pipe = Pipe.open();
          try
          {
            return new NioPipeSourceChannel(pipe.source(), factory, pipeline, sink, worker, type());
          }
          finally
          {
            worker.executeInIoThread(new Runnable()
            {
              private final ByteBuffer buffer = ByteBuffer.allocate(512);
              private boolean flushing;
              public void run()
              {
                try
                {
                  if (!flushing)
                  {
                    if (input.read(buffer) != -1)
                    {
                      buffer.flip();
                      pipe.sink().write(buffer);
                      buffer.compact();
                    }
                    else
                    {
                      flushing = true;
                    }
                  }
                  if (flushing)
                  {
                    if (buffer.hasRemaining())
                    {
                      pipe.sink().write(buffer);
                      buffer.compact();
                    }
                    else
                    {
                      pipe.sink().close();
                      return;
                    }
                  }
                  worker.executeInIoThread(this, true);
                }
                catch (Exception ex)
                {
                  ex.printStackTrace();
                  try
                  {
                    pipe.sink().close();
                  }
                  catch (IOException ignored)
                  {
                    //
                  }
                }
              }
            }, true);
          }
        }
      }
      catch (IOException e)
      {
        throw new RuntimeException(e);
      }
    }
  }

  private static class ProcessOutput extends AbstractProcessOutput
  {
    public ProcessOutput(Process process)
    {
      super(process);
    }

    @Override
    protected InputStream getInputStream()
    {
      return process.getInputStream();
    }

    public NioPipeChannel.Type type()
    {
      return NioPipeChannel.Type.PROCESS_STDOUT;
    }
  }

  private static class ProcessError extends AbstractProcessOutput
  {
    public ProcessError(Process process)
    {
      super(process);
    }

    @Override
    protected InputStream getInputStream()
    {
      return process.getErrorStream();
    }

    public NioPipeChannel.Type type()
    {
      return NioPipeChannel.Type.PROCESS_STDERR;
    }
  }

  private static class ConsoleInput implements Factory
  {
    public final NioPipeSourceChannel newChannel(ChannelFactory factory, ChannelPipeline pipeline,
                                                 NioPipeChannelSink sink, final NioWorker worker)
    {
      FileDescriptor fd = FileDescriptor.in;
      SelectorProvider provider = SelectorProvider.provider();
      return new NioPipeSourceChannel(newInstance(SOURCE_CHANNEL_CONSTRUCTOR, provider, fd),
                                      factory, pipeline, sink, worker, type());
    }

    public NioPipeChannel.Type type()
    {
      return NioPipeChannel.Type.CONSOLE_STDIN;
    }
  }

  private static class ConsoleOutput implements Factory
  {
    public final NioPipeSinkChannel newChannel(ChannelFactory factory, ChannelPipeline pipeline,
                                               NioPipeChannelSink sink, final NioWorker worker)
    {
      FileDescriptor fd = FileDescriptor.out;
      SelectorProvider provider = SelectorProvider.provider();
      return new NioPipeSinkChannel(newInstance(SINK_CHANNEL_CONSTRUCTOR, provider, fd),
                                    factory, pipeline, sink, worker, type());
    }

    public NioPipeChannel.Type type()
    {
      return NioPipeChannel.Type.CONSOLE_STDOUT;
    }
  }

  private static class ConsoleError implements Factory
  {
    public final NioPipeSinkChannel newChannel(ChannelFactory factory, ChannelPipeline pipeline,
                                               NioPipeChannelSink sink, final NioWorker worker)
    {
      FileDescriptor fd = FileDescriptor.err;
      SelectorProvider provider = SelectorProvider.provider();
      return new NioPipeSinkChannel(newInstance(SINK_CHANNEL_CONSTRUCTOR, provider, fd),
                                    factory, pipeline, sink, worker, type());
    }

    public NioPipeChannel.Type type()
    {
      return NioPipeChannel.Type.CONSOLE_STDERR;
    }
  }

  private static final Field FILTER_OUTPUT_STREAM_OUT;
  private static final Field FILTER_INPUT_STREAM_IN;
  private static final Class<? extends Pipe.SinkChannel> SINK_CHANNEL_CLASS;
  private static final Constructor<? extends Pipe.SinkChannel> SINK_CHANNEL_CONSTRUCTOR;
  private static final Class<? extends Pipe.SourceChannel> SOURCE_CHANNEL_CLASS;
  private static final Constructor<? extends Pipe.SourceChannel> SOURCE_CHANNEL_CONSTRUCTOR;

  static
  {
    try
    {
      FILTER_OUTPUT_STREAM_OUT = FilterOutputStream.class.getDeclaredField("out");
      FILTER_OUTPUT_STREAM_OUT.setAccessible(true);

      FILTER_INPUT_STREAM_IN = FilterInputStream.class.getDeclaredField("in");
      FILTER_INPUT_STREAM_IN.setAccessible(true);

      Pipe pipe = Pipe.open();
      SINK_CHANNEL_CLASS = pipe.sink().getClass();
      SOURCE_CHANNEL_CLASS = pipe.source().getClass();

      SINK_CHANNEL_CONSTRUCTOR = SINK_CHANNEL_CLASS.getDeclaredConstructor(SelectorProvider.class, FileDescriptor.class);
      SINK_CHANNEL_CONSTRUCTOR.setAccessible(true);

      SOURCE_CHANNEL_CONSTRUCTOR = SOURCE_CHANNEL_CLASS.getDeclaredConstructor(SelectorProvider.class, FileDescriptor.class);
      SOURCE_CHANNEL_CONSTRUCTOR.setAccessible(true);

      pipe.source().close();
      pipe.sink().close();
    }
    catch (NoSuchFieldException e)
    {
      throw new RuntimeException(e);
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
    catch (NoSuchMethodException e)
    {
      throw new RuntimeException(e);
    }
  }

  private static <T> T newInstance(Constructor<T> constructor, Object... arguments)
  {
    try
    {
      return constructor.newInstance(arguments);
    }
    catch (InstantiationException e)
    {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e)
    {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e)
    {
      if (e.getCause() instanceof RuntimeException)
        throw (RuntimeException) e.getCause();
      throw new RuntimeException(e.getCause());
    }
  }

  private static OutputStream unwrap(FilterOutputStream s)
  {
    try
    {
      return OutputStream.class.cast(FILTER_OUTPUT_STREAM_OUT.get(s));
    }
    catch (IllegalAccessException e)
    {
      throw new RuntimeException(e);
    }
  }

  private static InputStream unwrap(FilterInputStream s)
  {
    try
    {
      return InputStream.class.cast(FILTER_INPUT_STREAM_IN.get(s));
    }
    catch (IllegalAccessException e)
    {
      throw new RuntimeException(e);
    }
  }
}
