package org.cooder.mos.ssh;

import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author renqianqian
 * @date 2021/6/20
 */
public class SshShellFactory implements ShellFactory {

    public static ThreadPoolExecutor SHELL_EXECUTOR;

    static {
        SHELL_EXECUTOR = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(2), Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public Command createShell(ChannelSession channel) throws IOException {
        return new SshShellWrapper();
    }
}
