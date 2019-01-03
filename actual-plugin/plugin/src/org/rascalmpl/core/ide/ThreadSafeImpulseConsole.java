package org.rascalmpl.core.ide;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import io.usethesource.impulse.runtime.RuntimePlugin;

public enum ThreadSafeImpulseConsole {
	INSTANCE {
		public final Writer writer = new SyncWriter();

		@Override
		public Writer getWriter() {
			return writer;
		}
	};
	
	public abstract Writer getWriter();
	

	private static final class SyncWriter extends Writer {
		private final PrintStream target = RuntimePlugin.getInstance().getConsoleStream();

		private final Queue<ByteBuffer> queuedWrites = new ConcurrentLinkedDeque<>();
		private final Semaphore flush = new Semaphore(0, true);
		private final static int FLUSH_EVERY_WRITES = 100;

		private final Thread actual = new Thread(() -> {
			while (true) {
				try {
					flush.tryAcquire(FLUSH_EVERY_WRITES, 20, TimeUnit.MILLISECONDS);
					flush.drainPermits(); // avoid multiple flushes
					ByteBuffer toWrite;
					while ((toWrite = queuedWrites.poll()) != null) {
						target.write(toWrite.array(), toWrite.arrayOffset(), toWrite.limit());
					}

				} catch (InterruptedException ie) {
					target.close();
					return;
				}
			}
		});
		
		public SyncWriter() {
			actual.setName("Thread Safe Writer to the Impulse Console");
			actual.setDaemon(true);
			actual.start();
		}

		

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			write(CharBuffer.wrap(cbuf, off, len));
		}

		@Override
		public void write(String str, int off, int len) throws IOException {
			write(CharBuffer.wrap(str, off, len));
		}

		private void write(CharBuffer chars) {
			queuedWrites.add(StandardCharsets.UTF_8.encode(chars));
			flush.release();
		}
		
		@Override
		public void write(int c) throws IOException {
			write(CharBuffer.wrap(new char[] {(char)c }));
		}
		
		@Override
		public void flush() throws IOException {
			flush.release(FLUSH_EVERY_WRITES);
		}

		@Override
		public void close() throws IOException {
			actual.interrupt();
		}
		
	}
}

