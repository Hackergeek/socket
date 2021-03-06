package 使用NIO实现的文件下载;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NIOClient {
	static int SIZE = 100;
	static InetSocketAddress ip = new InetSocketAddress("localhost", 12345);
	static CharsetEncoder encoder = Charset.forName("GB2312").newEncoder();

	static class Download implements Runnable {
		protected int index;

		public Download(int index) {
			this.index = index;
		}

		public void run() {
			try {
				long start = System.currentTimeMillis();
				// 创建SocketChannel对象
				SocketChannel client = SocketChannel.open();
				// 将通道设置为非阻塞
				client.configureBlocking(false);
				// 创建Selector对象
				Selector selector = Selector.open();
				// 为了将Channel和Selector配合使用，必须将channel注册到selector上
				// 与Selector一起使用时，Channel必须处于非阻塞模式下。这意味着不能将FileChannel与Selector一起使用，
				// 因为FileChannel不能切换到非阻塞模式。而套接字通道都可以。
				client.register(selector, SelectionKey.OP_CONNECT);
				// 当向Selector注册Channel时，Channel.register()方法会返回一个SelectionKey
				// 对象。
				// 这个对象代表了注册到该Selector的通道。可以通过SelectionKey的selectedKeySet()方法访问这些对象
				client.connect(ip);
				// 分配缓冲区大小
				ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
				int total = 0;
				FOR: for (;;) {
					// select()阻塞到至少有一个通道在你注册的事件上就绪了。
					selector.select();
					Iterator<SelectionKey> iterator = selector.selectedKeys()
							.iterator();
					while (iterator.hasNext()) {
						SelectionKey key = iterator.next();
						// 每次迭代末尾的keyIterator.remove()调用。Selector不会自己从已选择键集中移除SelectionKey实例。
						// 必须在处理完通道时自己移除。下次该通道变成就绪时，Selector会再次将其放入已选择键集中。
						iterator.remove();
						if (key.isConnectable()) {
							SocketChannel channel = (SocketChannel) key
									.channel();
							if (channel.isConnectionPending()) {
								channel.finishConnect();
							}
							channel.write(encoder.encode(CharBuffer
									.wrap("Hello from " + index)));
							channel.register(selector, SelectionKey.OP_READ);
						} else if (key.isReadable()) {
							SocketChannel channel = (SocketChannel) key
									.channel();
							int count = channel.read(buffer);
							if (count > 0) {
								total += count;
								buffer.clear();
							} else {
								client.close();
								break FOR;
							}
						}
					}
				}
				double last = (System.currentTimeMillis() - start) * 1.0 / 1000;
				System.out.println("Thread " + index + " downloaded " + total
						+ "bytes in " + last + "s.");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		ExecutorService exec = Executors.newFixedThreadPool(SIZE);
		for (int index = 0; index < SIZE; index++) {
			exec.execute(new Download(index));
		}
		exec.shutdown();
	}

}
