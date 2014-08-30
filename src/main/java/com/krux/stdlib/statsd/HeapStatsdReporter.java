package com.krux.stdlib.statsd;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.TimerTask;

import com.krux.stdlib.KruxStdLib;
import com.sun.management.UnixOperatingSystemMXBean;

public class HeapStatsdReporter extends TimerTask {

    public HeapStatsdReporter() {

    }

    @Override
    public void run() {
        // Getting the runtime reference from system
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        KruxStdLib.STATSD.gauge("heap_used", usedMemory);

        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        KruxStdLib.STATSD.gauge("threads_live", bean.getThreadCount());

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof UnixOperatingSystemMXBean) {
            KruxStdLib.STATSD.gauge("open_fd", ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
        }
    }

}
