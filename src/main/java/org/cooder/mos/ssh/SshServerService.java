package org.cooder.mos.ssh;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import java.io.File;
import java.io.IOException;

/**
 * @author renqianqian
 * @date 2021/6/20
 */
public class SshServerService {

    private SshServer sshd;

    public SshServerService() {
        this.sshd = SshServer.setUpDefaultServer();
        sshd.setPort(2020);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("key.ser").toPath()));
        sshd.setPasswordAuthenticator(AcceptAllPasswordAuthenticator.INSTANCE);
        sshd.setShellFactory(new SshShellFactory());
    }

    public void start() throws IOException {
        this.sshd.start();

        Object o = new Object();
        synchronized (o) {
            try {
                o.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() throws IOException {
        this.sshd.close();
    }
}
