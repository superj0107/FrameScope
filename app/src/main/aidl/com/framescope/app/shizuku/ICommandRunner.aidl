package com.framescope.app.shizuku;

import com.framescope.app.shizuku.CommandResult;
import com.framescope.app.shizuku.SuspendResult;

interface ICommandRunner {
    String executeCommand(String command);
    int executeCommandWithExitCode(String command);
    CommandResult executeCommandWithResult(String command);
    String readProcStat();
    String getThermalTemperatures();
    SuspendResult suspendPackages(in String[] packageNames, boolean suspended);
    int setAppOpMode(in String[] packageNames, int opCode, int mode);
    void destroy();
}
