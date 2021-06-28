package org.cooder.mos.ssh;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.cooder.mos.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.cooder.mos.ssh.SshShellFactory.SHELL_EXECUTOR;

/**
 * @author renqianqian
 * @date 2021/6/20
 */
public class SshShellWrapper implements Command {

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private SshShell shell;


    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
        this.shell = new SshShell("/", this.in, this.out, channel.getSession().getUsername(), this.callback);
        SHELL_EXECUTOR.submit(shell);
    }

    @Override
    public void destroy(ChannelSession channel) throws Exception {
        Utils.flush(shell.out, shell.err);
        Utils.closeAll(shell.in, shell.out, shell.err);
        Utils.closeAll(in, out, err);
    }
}
