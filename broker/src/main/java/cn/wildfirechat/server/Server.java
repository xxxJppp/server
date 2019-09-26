/*
 * This file is part of the Wildfire Chat package.
 * (c) Heavyrain2012 <heavyrain.lee@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package cn.wildfirechat.server;

import io.tio.TioClientStarter;

public class Server {
    public static void main(String[] args) throws Exception {
        io.moquette.server.Server.start(args);
        TioClientStarter.start(args);
    }
}
