package org.cooder.mos;

import org.cooder.mos.device.FileDisk;
import org.cooder.mos.ssh.SshServerService;

import java.io.IOException;

/**
 * @author renqianqian
 * @date 2021/6/20
 */
public class SshServer {

    public static void main(String[] args) throws IOException {
        FileDisk disk = new FileDisk("mos-disk");
        MosSystem.fileSystem().bootstrap(disk, false);

        SshServerService sshServer = new SshServerService();
        try {
            sshServer.start();
        } finally {
            sshServer.close();
            MosSystem.fileSystem().shutdown();
        }
    }
}
