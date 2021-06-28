/*
 * This file is part of MOS
 * <p>
 * Copyright (c) 2021 by cooder.org
 * <p>
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */
package org.cooder.mos.shell;

import org.cooder.mos.MosSystem;
import org.cooder.mos.Utils;
import org.cooder.mos.fs.FileDescriptor;
import org.cooder.mos.fs.IFileSystem;
import org.cooder.mos.shell.command.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

@Command(name = "",
                subcommands = { HelpCommand.class, Mkdir.class, ListCommand.class, Cat.class, Echo.class, Pwd.class,
                        Remove.class, Touch.class })
public class Shell implements Runnable {

    private String rootPath;
    private FileDescriptor current;

    public InputStream in = MosSystem.in;
    public PrintStream out = MosSystem.out;
    public PrintStream err = MosSystem.err;

    public Shell(String rootPath) {
        this.rootPath = rootPath;
        this.current = MosSystem.fileSystem().find(new String[]{rootPath});
    }

    public String currentPath() {
        return current.isRoot() ? current.getName() : current.getPath();
    }

    public void loop() {
        Scanner scanner = null;
        try {
            scanner = new Scanner(in);
            while (true) {
                prompt();

                String cmd = scanner.nextLine().trim();
                if ("exit".equals(cmd)) {
                    out.println("bye~");
                    break;
                } else if (cmd.length() == 0) {
                    continue;
                }

                try {
                    String[] as = Utils.parseArgs(cmd);
                    new CommandLine(this).execute(as);
                } catch (Exception e) {
                    err.println(e.getMessage());
                }
            }
        } finally {
            Utils.close(scanner);
        }
    }

    @Command(name = "format", hidden = true)
    public void format() throws IOException {
        MosSystem.fileSystem().format();
        resetToRootPath();
        out.println("disk format success.");
    }

    @Command(name = "cd", header = "Change the shell working directory.")
    public void cd(@Parameters(paramLabel = "<path>") String path) {
        String[] paths = null;

        if (path.equals("/")) {
            current = MosSystem.fileSystem().find(new String[] { rootPath });
            return;
        }

        if (path.equals("..")) {
            if (current.isRoot()) {
                return;
            }
            String[] ps = Utils.normalizePath(current.getParentPath());
            current = MosSystem.fileSystem().find(ps);
            return;
        }

        paths = absolutePath(path);
        FileDescriptor node = MosSystem.fileSystem().find(paths);
        if (node == null) {
            err.println(path + ": No such file or directory");
            return;
        }

        if (!node.isDir()) {
            err.println(path + ": Not a directory");
            return;
        }

        current = node;
    }

    public String[] absolutePath(String path) {
        if (path.equals(".")) {
            path = current.getPath();
        } else if (path.equals("..")) {
            if (current.isRoot()) {
                path = current.getPath();
            } else {
                path = current.getParentPath();
            }
        } else if (!path.startsWith("/")) {
            path = current.getPath() + IFileSystem.separator + path;
        }
        return Utils.normalizePath(path);
    }

    @Override
    public void run() {
        // no-op
    }

    private void prompt() {
        out.print(String.format("root@mos-nil:%s$ ", currentPath()));
    }

    private void resetToRootPath() {
        this.current = MosSystem.fileSystem().find(new String[]{rootPath});
    }
}
