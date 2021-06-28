package org.cooder.mos.ssh;

import picocli.CommandLine;

import java.io.PrintStream;

/**
 * @author renqianqian
 * @date 2021/6/20
 */
public class SshErrorMessageHandler implements CommandLine.IParameterExceptionHandler {

    private PrintStream err;

    public SshErrorMessageHandler(PrintStream err) {
        this.err = err;
    }

    @Override
    public int handleParseException(CommandLine.ParameterException ex, String[] args) throws Exception {
        CommandLine cmd = ex.getCommandLine();

        CommandLine.UnmatchedArgumentException.printSuggestions(ex, err);
        CommandLine.Model.CommandSpec spec = cmd.getCommandSpec();

        return cmd.getExitCodeExceptionMapper() != null
                ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                : spec.exitCodeOnInvalidInput();
    }
}
