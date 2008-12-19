/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.strategy;

import java.io.File;
import java.io.IOException;

import org.apache.camel.component.file.ExclusiveReadLockStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Acquires exclusive read lock to the given file. Will wait until the lock is granted.
 * After granting the read lock it is realeased, we just want to make sure that when we start
 * consuming the file its not currently in progress of being written by third party.
 */
public class FileRenameExclusiveReadLockStrategy implements ExclusiveReadLockStrategy {
    private static final transient Log LOG = LogFactory.getLog(FileRenameExclusiveReadLockStrategy.class);
    private long timeout;

    public boolean acquireExclusiveReadLock(File file) throws IOException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for exclusive read lock to file: " + file);
        }

        // the trick is to try to rename the file, if we can rename then we have exclusive read
        // since its a remote file we can not use java.nio to get a RW lock
        String originalName = file.getName();
        File tempFile = new File(file.getParent(), originalName + ".camelExclusiveReadLock");

        long start = System.currentTimeMillis();

        boolean exclusive = false;
        while (!exclusive) {
            // timeout check
            if (timeout > 0) {
                long delta = System.currentTimeMillis() - start;
                if (delta > timeout) {
                    LOG.debug("Could not acquire read lock within " + timeout + " millis. Will skip the file: " + file);
                    // we could not get the lock within the timeout period, so return false
                    return false;
                }
            }

            exclusive = file.renameTo(tempFile);
            if (exclusive) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Acquired exclusive read lock to file: " + file);
                }
                // rename it back so we can read it
                tempFile.renameTo(file);
            } else {
                LOG.trace("Exclusive read lock not granted. Sleeping for 1000 millis.");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        return true;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets an optional timeout period.
     * <p/>
     * If the readlock could not be granted within the timeperiod then the wait is stopped and the
     * {@link #acquireExclusiveReadLock(java.io.File)} returns <tt>false</tt>.
     *
     * @param timeout period in millis
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
