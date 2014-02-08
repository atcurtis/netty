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

import org.jboss.netty.channel.ReceiveBufferSizePredictor;
import org.jboss.netty.channel.ReceiveBufferSizePredictorFactory;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/8/14
 * Time: 3:06 PM
 * To change this template use File | Settings | File Templates.
 */
public interface NioSelectableChannelConfig extends NioChannelConfig
{
  /**
   * Returns the {@link org.jboss.netty.channel.ReceiveBufferSizePredictor} which predicts the
   * number of readable bytes in the socket receive buffer.  The default
   * predictor is <tt>{@link org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictor}(64, 1024, 65536)</tt>.
   */
  ReceiveBufferSizePredictor getReceiveBufferSizePredictor();

  /**
   * Sets the {@link ReceiveBufferSizePredictor} which predicts the
   * number of readable bytes in the socket receive buffer.  The default
   * predictor is <tt>{@link org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictor}(64, 1024, 65536)</tt>.
   */
  void setReceiveBufferSizePredictor(ReceiveBufferSizePredictor predictor);

  /**
   * Returns the {@link org.jboss.netty.channel.ReceiveBufferSizePredictorFactory} which creates a new
   * {@link ReceiveBufferSizePredictor} when a new channel is created and
   * no {@link ReceiveBufferSizePredictor} was set.  If no predictor was set
   * for the channel, {@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}
   * will be called with the new predictor.  The default factory is
   * <tt>{@link org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictorFactory}(64, 1024, 65536)</tt>.
   */
  ReceiveBufferSizePredictorFactory getReceiveBufferSizePredictorFactory();

  /**
   * Sets the {@link ReceiveBufferSizePredictor} which creates a new
   * {@link ReceiveBufferSizePredictor} when a new channel is created and
   * no {@link ReceiveBufferSizePredictor} was set.  If no predictor was set
   * for the channel, {@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}
   * will be called with the new predictor.  The default factory is
   * <tt>{@link org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictorFactory}(64, 1024, 65536)</tt>.
   */
  void setReceiveBufferSizePredictorFactory(ReceiveBufferSizePredictorFactory predictorFactory);

}
