/*
 * Copyright (c) 2004 Persistit Corporation. All Rights Reserved.
 *
 * The Java source code is the confidential and proprietary information
 * of Persistit Corporation ("Confidential Information"). You shall
 * not disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Persistit Corporation.
 *
 * PERSISTIT CORPORATION MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE
 * SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
 * A PARTICULAR PURPOSE, OR NON-INFRINGEMENT. PERSISTIT CORPORATION SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING,
 * MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 * 
 * Created on Feb 19, 2004
 */
package com.persistit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import com.persistit.logging.AbstractPersistitLogger;
import com.persistit.logging.LogTemplate;

/**
 *
 */
public class LogBase {
    private final static String THIS_CLASS_NAME = "com.persistit.LogBase";

    final static int ALL = 0xFFFF;
    final static int LIFECYCLE = 0x0001;
    final static int POOL = 0x0002;
    final static int JOURNAL = 0x0004;
    final static int GAR = 0x0008;
    final static int FAIL = 0x0010;
    final static int IO = 0x0020;
    final static int PERSISTIT = 0x0040;
    final static int TEST = 0x0080;
    final static int DEBUG = 0x0100;
    final static int RMI = 0x0200;

    final static String[] TYPE_NAMES = { "ALL", "LIFECYCLE", "POOL", "J",
            "GAR", "FAIL", "IO", "PERSISTIT", "TEST", "DEBUG", "RMI", };

    private final static int FINEST = AbstractPersistitLogger.FINEST;
    private final static int FINER = AbstractPersistitLogger.FINER;
    private final static int FINE = AbstractPersistitLogger.FINE;
    private final static int INFO = AbstractPersistitLogger.INFO;
    private final static int WARNING = AbstractPersistitLogger.WARNING;
    private final static int SEVERE = AbstractPersistitLogger.SEVERE;

    private static LogTemplate[] _templateArray = new LogTemplate[70];

    private static int _logId = 0;

    final static int LOG_GENERAL = addLogTemplate(INFO, LIFECYCLE,
            "Persistit: {0}");
    final static int LOG_DEBUG = addLogTemplate(INFO, DEBUG, "{0}");
    final static int LOG_COPYRIGHT = addLogTemplate(INFO, LIFECYCLE,
            Persistit.copyright());
    final static int LOG_START = addLogTemplate(INFO, LIFECYCLE, "START");
    final static int LOG_END = addLogTemplate(INFO, LIFECYCLE, "END");
    public final static int LOG_EXCEPTION = addLogTemplate(SEVERE, ALL,
            "EXCEPTION");
    final static int LOG_INIT_ALLOCATE_BUFFERS = addLogTemplate(INFO,
            LIFECYCLE, "INIT_ALLOCATE_BUFFERS");
    final static int LOG_INIT_OPEN_VOLUME = addLogTemplate(INFO, LIFECYCLE,
            "INIT_OPEN_VOLUME");
    final static int LOG_JOURNAL_WRITE_ERROR = addLogTemplate(SEVERE,
            LIFECYCLE, "JOURNAL_WRITE_ERROR");
    final static int LOG_RECOVERY_DONE = addLogTemplate(INFO, JOURNAL,
            "RECOVERY_DONE");
    final static int LOG_RECOVERY_FAILURE = addLogTemplate(SEVERE, LIFECYCLE,
            "RECOVERY_FAILURE");
    final static int LOG_RECOVERY_RECORD = addLogTemplate(DEBUG, JOURNAL,
            "RECOVERY_RECORD");
    final static int LOG_RECOVERY_PLAN = addLogTemplate(INFO, JOURNAL,
            "RECOVERY_PLAN");
    final static int LOG_RECOVERY_PROGRESS = addLogTemplate(INFO, JOURNAL,
            "RECOVERY_PROGRESS");
    final static int LOG_RECOVERY_EXCEPTION = addLogTemplate(WARNING, JOURNAL,
            "RECOVERY_EXCEPTION");
    final static int LOG_CHECKPOINT_PROPOSED = addLogTemplate(INFO, JOURNAL,
            "CHECKPOINT_PROPOSED");
    final static int LOG_CHECKPOINT_WRITTEN = addLogTemplate(INFO, JOURNAL,
            "CHECKPOINT_WRITTEN");
    final static int LOG_CHECKPOINT_RECOVERED = addLogTemplate(INFO, JOURNAL,
            "CHECKPOINT_RECOVERED");
    final static int LOG_TXN_EXCEPTION = addLogTemplate(SEVERE, PERSISTIT,
            "TXN_EXCEPTION");
    final static int LOG_TXN_NOT_COMMITTED = addLogTemplate(FINE, PERSISTIT,
            "TXN_NOT_COMMITTED");
    final static int LOG_INIT_CREATE_GUI = addLogTemplate(FINE, LIFECYCLE,
            "INIT_CREATE_GUI");
    public final static int LOG_MBEAN_REGISTRATION = addLogTemplate(FINE,
            LIFECYCLE, "MBEAN_REGISTRATION");
    public final static int LOG_MBEAN_EXCEPTION = addLogTemplate(WARNING,
            LIFECYCLE, "MBEAN_EXCEPTION");
    final static int LOG_DEFERRED_DEALLOC_FAILED = addLogTemplate(WARNING,
            PERSISTIT, "DEFERRED_DEALLOC_FAILED");
    final static int LOG_PENDING_TIMEOUT = addLogTemplate(WARNING, PERSISTIT,
            "PENDING_TIMEOUT");
    final static int LOG_SET_GARBAGE_HEADER = addLogTemplate(FINE, GAR,
            "SET_GARBAGE_HEADER");
    final static int LOG_DEALLOCGC1 = addLogTemplate(FINER, GAR, "DEALLOCGC1");
    final static int LOG_DEALLOCGC2 = addLogTemplate(FINEST, GAR, "DEALLOCGC2");
    final static int LOG_DEALLOCGC3 = addLogTemplate(FINEST, GAR, "DEALLOCGC3");
    final static int LOG_DEALLOCGC4 = addLogTemplate(FINER, GAR, "DEALLOCGC4");
    final static int LOG_DEALLOCGC5 = addLogTemplate(FINER, GAR, "DEALLOCGC5");
    final static int LOG_DEALLOCGC6 = addLogTemplate(FINER, GAR, "DEALLOCGC6");
    final static int LOG_DEALLOCGC7 = addLogTemplate(FINEST, GAR, "DEALLOCGC7");
    final static int LOG_READ_UNSAFE = addLogTemplate(FINE, POOL, "READ_UNSAFE");
    final static int LOG_READ_OK = addLogTemplate(FINEST, POOL, "READ_OK");
    final static int LOG_READ_IOE = addLogTemplate(SEVERE, POOL, "READ_IOE");
    final static int LOG_WRITE_IOE = addLogTemplate(SEVERE, POOL, "WRITE_IOE");
    final static int LOG_ALLOC_RETRY = addLogTemplate(FINER, GAR, "ALLOC_RETRY");
    final static int LOG_ALLOC_GARROOT = addLogTemplate(FINER, GAR,
            "ALLOC_GARROOT");
    final static int LOG_ALLOC_GAR = addLogTemplate(FINEST, GAR, "ALLOC_GAR");
    final static int LOG_ALLOC_GAR_END = addLogTemplate(FINEST, GAR,
            "ALLOC_GAR_END");
    final static int LOG_ALLOC_GAR_UPDATE = addLogTemplate(FINEST, GAR,
            "ALLOC_GAR_UPDATE");
    final static int LOG_EXTEND_NORMAL = addLogTemplate(FINE, POOL,
            "EXTEND_NORMAL");
    final static int LOG_EXTEND_LARGER = addLogTemplate(DEBUG, POOL,
            "EXTEND_LARGER");
    final static int LOG_EXTEND_IOE = addLogTemplate(SEVERE, POOL, "EXTEND_IOE");
    final static int LOG_RMI_SERVER = addLogTemplate(FINE, RMI, "RMI_SERVER");
    final static int LOG_UNINDEXED_PAGE = addLogTemplate(INFO, PERSISTIT,
            "UNINDEXED_PAGE");
    final static int LOG_RMI_EXCEPTION = addLogTemplate(WARNING, RMI,
            "RMI_SERVER");
    final static int LOG_SHUTDOWN_HOOK = addLogTemplate(INFO, LIFECYCLE,
            "SHUTDOWN_HOOK");
    final static int LOG_WAIT_FOR_CLOSE = addLogTemplate(INFO, LIFECYCLE,
            "WAIT_FOR_CLOSE");
    final static int LOG_STRANDED = addLogTemplate(SEVERE, LIFECYCLE,
            "STRANDED");

    private static ResourceBundle _logBundle;
    private static boolean _logBundleLoadFailed = false;

    private int _minimumLogLevel = INFO;

    protected final Persistit _persistit;

    public LogBase(final Persistit persistit) {
        _persistit = persistit;
    }

    boolean isLoggable(int messageId) {
        if ((messageId & 0xFF) < _minimumLogLevel)
            return false;
        return _persistit.getPersistitLogger().isLoggable(
                logTemplate(messageId));
    }

    public LogTemplate logTemplate(int messageId) {
        return _templateArray[messageId >>> 8];
    }

    void log(String message) {
        log(LOG_GENERAL, message);
    }

    void log(int messageId, Object... args) {
        _persistit.getPersistitLogger().log(logTemplate(messageId), args);
    }

    private int lookup(String[] array, String s) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] != null && array[i].equalsIgnoreCase(s))
                return i;
        }
        return -1;
    }

    protected static int addLogTemplate(int level, int subsystem,
            String template) {
        int messageId;

        messageId = _logId++;
        try {
            // Try to load up the resource bundle
            if (_logBundle == null && !_logBundleLoadFailed) {
                _logBundle = ResourceBundle.getBundle(THIS_CLASS_NAME);
            }
            if (_logBundle != null) {
                try {
                    String translatedTemplate = _logBundle.getString(template);
                    if (translatedTemplate != null)
                        template = translatedTemplate;
                } catch (MissingResourceException mre) {
                    // ignore this -- just log the abstract name.
                }
            }
        } catch (MissingResourceException ex) {
            // We can still function without the resources ...
            System.err.println(ex.toString());
            _logBundleLoadFailed = false;
        }

        if (messageId >= _templateArray.length) {
            LogTemplate[] newArray = new LogTemplate[messageId + 5];
            System.arraycopy(_templateArray, 0, newArray, 0,
                    _templateArray.length);
            _templateArray = newArray;
        }

        LogTemplate lt = new LogTemplate(level, subsystem, template);
        lt.setEnabled(level > FINE);
        _templateArray[messageId] = lt;

        return messageId * 256 + level;
    }

    public void logflush() {
        _persistit.getPersistitLogger().flush();
    }

    public void logstart() throws Exception {
        if (!_persistit.getPersistitLogger().isOpen()) {
            _persistit.getPersistitLogger().open();
        }
        log(LOG_COPYRIGHT);
        log(LOG_START, Persistit.version(), new Date());
    }

    public void logend() {
        log(LOG_END);
        _persistit.getPersistitLogger().close();
    }

    public void setLogEnabled(String flags, int baseLevel) throws Exception {
        StringTokenizer st1 = new StringTokenizer(flags, ",");
        while (st1.hasMoreTokens()) {
            String s = st1.nextToken();
            StringTokenizer st2 = new StringTokenizer(s, ":");
            int level = baseLevel;
            int subsystem = 0;
            if (st2.hasMoreTokens()) {
                subsystem = lookup(TYPE_NAMES, st2.nextToken());
            }
            if (st2.hasMoreTokens()) {
                level = lookup(AbstractPersistitLogger.LEVEL_NAMES,
                        st2.nextToken());
            }

            if (subsystem == -1 || level == -1) {
                throw new IllegalArgumentException(
                        "Bad log level specifaction \"" + s + "\" in \""
                                + flags + "\"");
            }
            long typeMask = 0;
            if (subsystem == 0)
                typeMask = -1;
            else if (subsystem > 0)
                typeMask = 1 << (subsystem - 1);
            setEnabled(typeMask, level);
        }
    }

    public void setEnabled(long typeMask, int level) {
        for (int i = 0; i < _templateArray.length; i++) {
            LogTemplate lt = _templateArray[i];
            if (lt != null) {
                if ((lt.getType() & typeMask) != 0 && lt.getLevel() >= level) {
                    lt.setEnabled(true);
                    if (level < _minimumLogLevel)
                        _minimumLogLevel = level;
                }
            }
        }
    }

    public static String detailString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

}