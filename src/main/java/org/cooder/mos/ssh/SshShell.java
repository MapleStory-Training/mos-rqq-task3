package org.cooder.mos.ssh;

import org.apache.sshd.server.ExitCallback;
import org.cooder.mos.Utils;
import org.cooder.mos.shell.Shell;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author renqianqian
 * @date 2021/6/20
 */
public class SshShell extends Shell {

    private String userName;
    private Terminal terminal;
    private ExitCallback callback;

    public SshShell(String rootPath, InputStream in, OutputStream out, String userName, ExitCallback callback) throws IOException {
        super(rootPath);
        this.terminal = TerminalBuilder.builder().streams(in, out).build();
        this.callback = callback;
        this.in = terminal.input();
        this.out = new PrintStream(terminal.output());
        this.err = new PrintStream(terminal.output());
        this.userName = userName;
    }

    @Override
    public void loop() {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(this.terminal)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .completer(aggregateCompleter())
                .build();
        try {
            while (true) {
                String cmd = reader.readLine(prompt());
                if ("exit".equals(cmd)) {
                    out.println("bye~");
                    break;
                } else if (cmd.length() == 0) {
                    continue;
                }

                try {
                    String[] as = Utils.parseArgs(cmd);
                    new CommandLine(this).setParameterExceptionHandler(new SshErrorMessageHandler(this.err)).execute(as);
                } catch (Exception e) {
                    err.println(e.getMessage());
                }
            }
        } finally {
            callback.onExit(0);
        }
    }

    @Override
    public void run() {
        this.loop();
    }

    private String prompt() {
        return String.format("%s@mos-nil:%s$ ", this.userName, currentPath());
    }

    Completer aggregateCompleter() {
        ArgumentCompleter commandNameCompleter = new ArgumentCompleter(
                new StringsCompleter("cat", "echo", "help", "ll", "ls", "mkdir", "pwd", "rm", "touch"),
                NullCompleter.INSTANCE
        );

        AggregateCompleter aggregateCompleter = new AggregateCompleter(commandNameCompleter);
        return aggregateCompleter;
    }
}
