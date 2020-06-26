package net.minecraft;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrashReport {

    private static final Logger LOGGER = LogManager.getLogger();
    private final String title;
    private final Throwable exception;
    private final CrashReportCategory systemDetails = new CrashReportCategory(this, "System Details");
    private final List<CrashReportCategory> details = Lists.newArrayList();
    private File saveFile;
    private boolean trackingStackTrace = true;
    private StackTraceElement[] uncategorizedStackTrace = new StackTraceElement[0];

    public CrashReport(String s, Throwable throwable) {
        this.title = s;
        this.exception = throwable;
        this.initDetails();
    }

    private void initDetails() {
        this.systemDetails.setDetail("Minecraft Version", () -> {
            return SharedConstants.getCurrentVersion().getName();
        });
        this.systemDetails.setDetail("Minecraft Version ID", () -> {
            return SharedConstants.getCurrentVersion().getId();
        });
        this.systemDetails.setDetail("Operating System", () -> {
            return System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ") version " + System.getProperty("os.version");
        });
        this.systemDetails.setDetail("Java Version", () -> {
            return System.getProperty("java.version") + ", " + System.getProperty("java.vendor");
        });
        this.systemDetails.setDetail("Java VM Version", () -> {
            return System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor");
        });
        this.systemDetails.setDetail("Memory", () -> {
            Runtime runtime = Runtime.getRuntime();
            long i = runtime.maxMemory();
            long j = runtime.totalMemory();
            long k = runtime.freeMemory();
            long l = i / 1024L / 1024L;
            long i1 = j / 1024L / 1024L;
            long j1 = k / 1024L / 1024L;

            return k + " bytes (" + j1 + " MB) / " + j + " bytes (" + i1 + " MB) up to " + i + " bytes (" + l + " MB)";
        });
        this.systemDetails.setDetail("CPUs", (Object) Runtime.getRuntime().availableProcessors());
        this.systemDetails.setDetail("JVM Flags", () -> {
            List<String> list = (List) Util.getVmArguments().collect(Collectors.toList());

            return String.format("%d total; %s", list.size(), list.stream().collect(Collectors.joining(" ")));
        });
        this.systemDetails.setDetail("CraftBukkit Information", (CrashReportDetail) new org.bukkit.craftbukkit.CraftCrashReport()); // CraftBukkit
    }

    public String getTitle() {
        return this.title;
    }

    public Throwable getException() {
        return this.exception;
    }

    public void getDetails(StringBuilder stringbuilder) {
        if ((this.uncategorizedStackTrace == null || this.uncategorizedStackTrace.length <= 0) && !this.details.isEmpty()) {
            this.uncategorizedStackTrace = (StackTraceElement[]) ArrayUtils.subarray(((CrashReportCategory) this.details.get(0)).getStacktrace(), 0, 1);
        }

        if (this.uncategorizedStackTrace != null && this.uncategorizedStackTrace.length > 0) {
            stringbuilder.append("-- Head --\n");
            stringbuilder.append("Thread: ").append(Thread.currentThread().getName()).append("\n");
            stringbuilder.append("Stacktrace:\n");
            StackTraceElement[] astacktraceelement = this.uncategorizedStackTrace;
            int i = astacktraceelement.length;

            for (int j = 0; j < i; ++j) {
                StackTraceElement stacktraceelement = astacktraceelement[j];

                stringbuilder.append("\t").append("at ").append(stacktraceelement);
                stringbuilder.append("\n");
            }

            stringbuilder.append("\n");
        }

        Iterator iterator = this.details.iterator();

        while (iterator.hasNext()) {
            CrashReportCategory crashreportsystemdetails = (CrashReportCategory) iterator.next();

            crashreportsystemdetails.getDetails(stringbuilder);
            stringbuilder.append("\n\n");
        }

        this.systemDetails.getDetails(stringbuilder);
    }

    public String getExceptionMessage() {
        StringWriter stringwriter = null;
        PrintWriter printwriter = null;
        Object object = this.exception;

        if (((Throwable) object).getMessage() == null) {
            if (object instanceof NullPointerException) {
                object = new NullPointerException(this.title);
            } else if (object instanceof StackOverflowError) {
                object = new StackOverflowError(this.title);
            } else if (object instanceof OutOfMemoryError) {
                object = new OutOfMemoryError(this.title);
            }

            ((Throwable) object).setStackTrace(this.exception.getStackTrace());
        }

        String s;

        try {
            stringwriter = new StringWriter();
            printwriter = new PrintWriter(stringwriter);
            ((Throwable) object).printStackTrace(printwriter);
            s = stringwriter.toString();
        } finally {
            IOUtils.closeQuietly(stringwriter);
            IOUtils.closeQuietly(printwriter);
        }

        return s;
    }

    public String getFriendlyReport() {
        StringBuilder stringbuilder = new StringBuilder();

        stringbuilder.append("---- Minecraft Crash Report ----\n");
        stringbuilder.append("// ");
        stringbuilder.append(getErrorComment());
        stringbuilder.append("\n\n");
        stringbuilder.append("Time: ");
        stringbuilder.append((new SimpleDateFormat()).format(new Date()));
        stringbuilder.append("\n");
        stringbuilder.append("Description: ");
        stringbuilder.append(this.title);
        stringbuilder.append("\n\n");
        stringbuilder.append(this.getExceptionMessage());
        stringbuilder.append("\n\nA detailed walkthrough of the error, its code path and all known details is as follows:\n");

        for (int i = 0; i < 87; ++i) {
            stringbuilder.append("-");
        }

        stringbuilder.append("\n\n");
        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public boolean saveToFile(File file) {
        if (this.saveFile != null) {
            return false;
        } else {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            OutputStreamWriter outputstreamwriter = null;

            boolean flag;

            try {
                outputstreamwriter = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
                outputstreamwriter.write(this.getFriendlyReport());
                this.saveFile = file;
                boolean flag1 = true;

                return flag1;
            } catch (Throwable throwable) {
                CrashReport.LOGGER.error("Could not save crash report to {}", file, throwable);
                flag = false;
            } finally {
                IOUtils.closeQuietly(outputstreamwriter);
            }

            return flag;
        }
    }

    public CrashReportCategory getSystemDetails() {
        return this.systemDetails;
    }

    public CrashReportCategory addCategory(String s) {
        return this.addCategory(s, 1);
    }

    public CrashReportCategory addCategory(String s, int i) {
        CrashReportCategory crashreportsystemdetails = new CrashReportCategory(this, s);

        if (this.trackingStackTrace) {
            int j = crashreportsystemdetails.fillInStackTrace(i);
            StackTraceElement[] astacktraceelement = this.exception.getStackTrace();
            StackTraceElement stacktraceelement = null;
            StackTraceElement stacktraceelement1 = null;
            int k = astacktraceelement.length - j;

            if (k < 0) {
                System.out.println("Negative index in crash report handler (" + astacktraceelement.length + "/" + j + ")");
            }

            if (astacktraceelement != null && 0 <= k && k < astacktraceelement.length) {
                stacktraceelement = astacktraceelement[k];
                if (astacktraceelement.length + 1 - j < astacktraceelement.length) {
                    stacktraceelement1 = astacktraceelement[astacktraceelement.length + 1 - j];
                }
            }

            this.trackingStackTrace = crashreportsystemdetails.validateStackTrace(stacktraceelement, stacktraceelement1);
            if (j > 0 && !this.details.isEmpty()) {
                CrashReportCategory crashreportsystemdetails1 = (CrashReportCategory) this.details.get(this.details.size() - 1);

                crashreportsystemdetails1.trimStacktrace(j);
            } else if (astacktraceelement != null && astacktraceelement.length >= j && 0 <= k && k < astacktraceelement.length) {
                this.uncategorizedStackTrace = new StackTraceElement[k];
                System.arraycopy(astacktraceelement, 0, this.uncategorizedStackTrace, 0, this.uncategorizedStackTrace.length);
            } else {
                this.trackingStackTrace = false;
            }
        }

        this.details.add(crashreportsystemdetails);
        return crashreportsystemdetails;
    }

    private static String getErrorComment() {
        String[] astring = new String[]{"Who set us up the TNT?", "Everything's going to plan. No, really, that was supposed to happen.", "Uh... Did I do that?", "Oops.", "Why did you do that?", "I feel sad now :(", "My bad.", "I'm sorry, Dave.", "I let you down. Sorry :(", "On the bright side, I bought you a teddy bear!", "Daisy, daisy...", "Oh - I know what I did wrong!", "Hey, that tickles! Hehehe!", "I blame Dinnerbone.", "You should try our sister game, Minceraft!", "Don't be sad. I'll do better next time, I promise!", "Don't be sad, have a hug! <3", "I just don't know what went wrong :(", "Shall we play a game?", "Quite honestly, I wouldn't worry myself about that.", "I bet Cylons wouldn't have this problem.", "Sorry :(", "Surprise! Haha. Well, this is awkward.", "Would you like a cupcake?", "Hi. I'm Minecraft, and I'm a crashaholic.", "Ooh. Shiny.", "This doesn't make any sense!", "Why is it breaking :(", "Don't do that.", "Ouch. That hurt :(", "You're mean.", "This is a token for 1 free hug. Redeem at your nearest Mojangsta: [~~HUG~~]", "There are four lights!", "But it works on my machine."};

        try {
            return astring[(int) (Util.getNanos() % (long) astring.length)];
        } catch (Throwable throwable) {
            return "Witty comment unavailable :(";
        }
    }

    public static CrashReport forThrowable(Throwable throwable, String s) {
        while (throwable instanceof CompletionException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }

        CrashReport crashreport;

        if (throwable instanceof ReportedException) {
            crashreport = ((ReportedException) throwable).getReport();
        } else {
            crashreport = new CrashReport(s, throwable);
        }

        return crashreport;
    }

    public static void preload() {
        (new CrashReport("Don't panic!", new Throwable())).getFriendlyReport();
    }
}
