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

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioPipeSinkChannel;
import org.jboss.netty.channel.socket.nio.NioPipeSourceChannel;

/**
 * Created with IntelliJ IDEA.
 * User: atcurtis
 * Date: 2/7/14
 * Time: 10:12 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ProcessChannel extends Channel
{
  ProcessAddress getRemoteAddress();

  NioPipeSinkChannel getInputChannel();
  NioPipeSourceChannel getOutputChannel();
  NioPipeSourceChannel getErrorChannel();
}
