/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.store;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.h2.mvstore.Chunk;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.ObjectDataType;
import org.h2.store.fs.FilePath;
import org.h2.store.fs.FileUtils;
import org.h2.test.TestBase;
import org.h2.util.IOUtils;
import org.h2.util.Task;

/**
 * Tests concurrently accessing a tree map store.
 */
public class TestMVStoreConcurrent extends TestMVStore {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().testFromMain();
    }

    @Override
    public void test() throws Exception {
        FileUtils.createDirectories(getBaseDir());
        testInterruptReopenAsync();
        testInterruptReopenRetryNIO();
        testConcurrentSaveCompact();
        testConcurrentDataType();
        testConcurrentAutoCommitAndChange();
        testConcurrentReplaceAndRead();
        testConcurrentChangeAndCompact();
        testConcurrentChangeAndGetVersion();
        testConcurrentFree();
        testConcurrentStoreAndRemoveMap();
        testConcurrentStoreAndClose();
        testConcurrentOnlineBackup();
        testConcurrentMap();
        testConcurrentIterate();
        testConcurrentWrite();
        testConcurrentRead();
    }

    private void testInterruptReopenAsync() {
        testInterruptReopen("async:");
    }

    private void testInterruptReopenRetryNIO() {
        testInterruptReopen("retry:");
    }

    private void testInterruptReopen(String prefix) {
        String fileName = prefix + getBaseDir() + "/" + getTestName();
        FileUtils.delete(fileName);
        final MVStore s = new MVStore.Builder().
                fileName(fileName).
                cacheSize(0).
                open();
        final Thread mainThread = Thread.currentThread();
        Task task = new Task() {
            @Override
            public void call() throws Exception {
                while (!stop) {
                    mainThread.interrupt();
                    Thread.sleep(10);
                }
            }
        };
        try {
            MVMap<Integer, byte[]> map = s.openMap("data");
            task.execute();
            for (int i = 0; i < 1000 && !task.isFinished(); i++) {
                map.get(i % 1000);
                map.put(i % 1000, new byte[1024]);
                s.commit();
            }
        } finally {
            task.get();
            s.close();
        }
    }

    private void testConcurrentSaveCompact() {
        String fileName = "memFS:" + getTestName();
        FileUtils.delete(fileName);
        MVStore.Builder builder = new MVStore.Builder().
                fileName(fileName).
                cacheSize(0);
        try (final MVStore s = builder.open()) {
            s.setRetentionTime(0);
            final MVMap<Integer, Integer> dataMap = s.openMap("data");
            Task task = new Task() {
                @Override
                public void call() {
                    int i = 0;
                    while (!stop) {
                        s.compact(100, 1024 * 1024);
                        MVStore.TxCounter token = s.registerVersionUsage();
                        try {
                            dataMap.put(i % 1000, i * 10);
                        } finally {
                            s.deregisterVersionUsage(token);
                        }
                        s.commit();
                        i++;
                    }
                }
            };
            task.execute();
            for (int i = 0; i < 1000 && !task.isFinished(); i++) {
                s.compact(100, 1024 * 1024);
                MVStore.TxCounter token = s.registerVersionUsage();
                try {
                    dataMap.put(i % 1000, i * 10);
                } finally {
                    s.deregisterVersionUsage(token);
                }
                s.commit();
            }
            task.get();
        }
    }

    private void testConcurrentDataType() throws InterruptedException {
        final ObjectDataType type = new ObjectDataType();
        final Object[] data = new Object[]{
                null,
                -1,
                1,
                10,
                "Hello",
                new Object[]{ new byte[]{(byte) -1, (byte) 1}, null},
                new Object[]{ new byte[]{(byte) 1, (byte) -1}, 10},
                new Object[]{ new byte[]{(byte) -1, (byte) 1}, 20L},
                new Object[]{ new byte[]{(byte) 1, (byte) -1}, 5},
        };
        Arrays.sort(data, type);
        Task[] tasks = new Task[2];
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = new Task() {
                @Override
                public void call() {
                    Random r = new Random();
                    WriteBuffer buff = new WriteBuffer();
                    while (!stop) {
                        int a = r.nextInt(data.length);
                        int b = r.nextInt(data.length);
                        int comp;
                        if (r.nextBoolean()) {
                            comp = type.compare(a, b);
                        } else {
                            comp = -type.compare(b, a);
                        }
                        buff.clear();
                        type.write(buff, a);
                        buff.clear();
                        type.write(buff, b);
                        if (a == b) {
                            assertEquals(0, comp);
                        } else {
                            assertEquals(a > b ? 1 : -1, comp);
                        }
                    }
                }
            };
            tasks[i].execute();
        }
        try {
            Thread.sleep(100);
        } finally {
            for (Task t : tasks) {
                t.get();
            }
        }
    }

    private void testConcurrentAutoCommitAndChange() throws InterruptedException {
        String fileName = "memFS:" + getTestName();
        FileUtils.delete(fileName);
        MVStore.Builder builder = new MVStore.Builder()
                                    .fileName(fileName)
                                    .pageSplitSize(1000);
        try (MVStore s = builder.open()) {
            s.setRetentionTime(1000);
            s.setAutoCommitDelay(1);
            final CountDownLatch latch = new CountDownLatch(2);
            Task task = new Task() {
                @Override
                public void call() {
                    latch.countDown();
                    while (!stop) {
                        s.compact(100, 1024 * 1024);
                    }
                }
            };
            final MVMap<Integer, Integer> dataMap = s.openMap("data");
            final MVMap<Integer, Integer> dataSmallMap = s.openMap("dataSmall");
            s.openMap("emptyMap");
            final AtomicInteger counter = new AtomicInteger();
            Task task2 = new Task() {
                @Override
                public void call() {
                    latch.countDown();
                    while (!stop) {
                        int i = counter.getAndIncrement();
                        dataMap.put(i, i * 10);
                        dataSmallMap.put(i % 100, i * 10);
                        if (i % 100 == 0) {
                            dataSmallMap.clear();
                        }
                    }
                }
            };
            task.execute();
            task2.execute();
            latch.await();
            for (int i = 0; !task.isFinished() && !task2.isFinished() && i < 1000; i++) {
                MVMap<Integer, Integer> map = s.openMap("d" + (i % 3));
                map.put(0, i);
                s.commit();
            }
            task.get();
            task2.get();
            for (int i = 0; i < counter.get(); i++) {
                assertEquals(10 * i, dataMap.get(i).intValue());
            }
        }
    }

    private void testConcurrentReplaceAndRead() throws InterruptedException {
        final MVStore s = new MVStore.Builder().open();
        final MVMap<Integer, Integer> map = s.openMap("data");
        for (int i = 0; i < 100; i++) {
            map.put(i, i % 100);
        }
        Task task = new Task() {
            @Override
            public void call() {
                int i = 0;
                while (!stop) {
                    map.put(i % 100, i % 100);
                    i++;
                    if (i % 1000 == 0) {
                        s.commit();
                    }
                }
            }
        };
        task.execute();
        try {
            Thread.sleep(1);
            for (int i = 0; !task.isFinished() && i < 1000000; i++) {
                assertEquals(i % 100, map.get(i % 100).intValue());
            }
        } finally {
            task.get();
        }
        s.close();
    }

    private void testConcurrentChangeAndCompact() throws InterruptedException {
        String fileName = "memFS:" + getTestName();
        FileUtils.delete(fileName);
        final MVStore s = new MVStore.Builder().fileName(
                fileName).
                pageSplitSize(10).
                autoCommitDisabled().open();
        s.setRetentionTime(10000);
        try {
            Task task = new Task() {
                @Override
                public void call() {
                    while (!stop) {
                        s.compact(100, 1024 * 1024);
                    }
                }
            };
            task.execute();
            Task task2 = new Task() {
                @Override
                public void call() {
                    while (!stop) {
                        s.compact(100, 1024 * 1024);
                    }
                }
            };
            task2.execute();
            Thread.sleep(1);
            for (int i = 0; !task.isFinished() && !task2.isFinished() && i < 1000; i++) {
                MVMap<Integer, Integer> map = s.openMap("d" + (i % 3));
                map.put(0, i);
                map.get(0);
                s.commit();
            }
            task.get();
            task2.get();
        } finally {
            s.close();
        }
    }

    private static void testConcurrentChangeAndGetVersion() throws InterruptedException {
        for (int test = 0; test < 10; test++) {
            try (final MVStore s = new MVStore.Builder().autoCommitDisabled().open()) {
                s.setVersionsToKeep(10);
                final MVMap<Integer, Integer> m = s.openMap("data");
                m.put(1, 1);
                Task task = new Task() {
                    @Override
                    public void call() {
                        while (!stop) {
                            m.put(1, 1);
                            s.commit();
                        }
                    }
                };
                task.execute();
                Thread.sleep(1);
                for (int i = 0; i < 10000; i++) {
                    if (task.isFinished()) {
                        break;
                    }
                    for (int j = 0; j < 20; j++) {
                        m.put(1, 1);
                        s.commit();
                    }
                    s.setVersionsToKeep(15);
                    long version = s.getCurrentVersion() - 1;
                    try {
                        m.openVersion(version);
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                    s.setVersionsToKeep(20);
                }
                task.get();
                s.commit();
            }
        }
    }

    private void testConcurrentFree() throws InterruptedException {
        String fileName = "memFS:" + getTestName();
        for (int test = 0; test < 10; test++) {
            FileUtils.delete(fileName);
            final MVStore s1 = new MVStore.Builder().
                    fileName(fileName).autoCommitDisabled().open();
            s1.setRetentionTime(0);
            final int count = 200;
            for (int i = 0; i < count; i++) {
                MVMap<Integer, Integer> m = s1.openMap("d" + i);
                m.put(1, 1);
                if (i % 2 == 0) {
                    s1.commit();
                }
            }
            s1.close();
            MVStore.Builder builder = new MVStore.Builder().
                    fileName(fileName).autoCommitDisabled();
            try (final MVStore s = builder.open()) {
                s.setRetentionTime(0);
                s.setVersionsToKeep(0);
                final ArrayList<MVMap<Integer, Integer>> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    MVMap<Integer, Integer> m = s.openMap("d" + i);
                    list.add(m);
                }

                final AtomicInteger counter = new AtomicInteger();
                Task task = new Task() {
                    @Override
                    public void call() {
                        while (!stop) {
                            int x = counter.getAndIncrement();
                            if (x >= count) {
                                break;
                            }
                            MVMap<Integer, Integer> m = list.get(x);
                            m.clear();
                            s.removeMap(m);
                        }
                    }
                };
                task.execute();
                Thread.sleep(1);
                while (true) {
                    int x = counter.getAndIncrement();
                    if (x >= count) {
                        break;
                    }
                    MVMap<Integer, Integer> m = list.get(x);
                    m.clear();
                    s.removeMap(m);
                    if (x % 5 == 0) {
                        s.commit();
                    }
                }
                task.get();
                // this will mark old chunks as unused,
                // but not remove (and overwrite) them yet
                MVMap<Integer, Integer> m = s.openMap("dummy");
                m.put(0, 0);
                s.commit();
                // this will remove them, so we end up with
                // one unused one, and one active one
                m.put(1, 1);
                s.commit();
                m.put(2, 2);
                s.commit();

                Map<String, String> layoutMap = s.getLayoutMap();
                int chunkCount = 0;
                for (String k : layoutMap.keySet()) {
                    if (k.startsWith(DataUtils.LAYOUT_CHUNK)) {
                        // dead chunks may stay around for a little while
                        // discount them
                        Chunk<?> chunk = s.getFileStore().createChunk(layoutMap.get(k));
                        if (chunk.maxLenLive > 0) {
                            chunkCount++;
                        }
                    }
                }
                assertTrue("" + chunkCount, chunkCount < 3);
            }
        }
    }

    private void testConcurrentStoreAndRemoveMap() throws InterruptedException {
        String fileName = "memFS:" + getTestName();
        FileUtils.delete(fileName);
        try (MVStore s = openStore(fileName)) {
            int count = 200;
            for (int i = 0; i < count; i++) {
                MVMap<Integer, Integer> m = s.openMap("d" + i);
                m.put(1, 1);
            }
            final AtomicInteger counter = new AtomicInteger();
            Task task = new Task() {
                @Override
                public void call() {
                    while (!stop) {
                        counter.incrementAndGet();
                        s.commit();
                    }
                }
            };
            task.execute();
            Thread.sleep(1);
            for (int i = 0; i < count || counter.get() < count; i++) {
                MVMap<Integer, Integer> m = s.openMap("d" + i);
                m.put(1, 10);
                s.removeMap(m);
                if (task.isFinished()) {
                    break;
                }
            }
            task.get();
        }
    }

    private void testConcurrentStoreAndClose() throws InterruptedException {
        String fileName = "memFS:" + getTestName();
        for (int i = 0; i < 10; i++) {
            FileUtils.delete(fileName);
            try (MVStore s = openStore(fileName)) {
                final AtomicInteger counter = new AtomicInteger();
                Task task = new Task() {
                    @Override
                    public void call() {
                        while (!stop) {
                            s.setStoreVersion(counter.incrementAndGet());
                            s.commit();
                        }
                    }
                };
                task.execute();
                while (counter.get() < 5) {
                    Thread.sleep(1);
                }
                try {
                    s.close();
                    // sometimes closing works, in which case
                    // storing must fail at some point (not necessarily
                    // immediately)
                    for (int x = counter.get(), y = x + 2; x <= y; x++) {
                        Thread.sleep(1);
                    }
                    Exception e = task.getException();
                    if (e != null) {
                        checkErrorCode(DataUtils.ERROR_CLOSED, e);
                    }
                } catch (MVStoreException e) {
                    // sometimes storing works, in which case
                    // closing must fail
                    assertEquals(DataUtils.ERROR_WRITING_FAILED, e.getErrorCode());
                    task.get();
                }
            }
        }
    }

    /**
     * Test the concurrent map implementation.
     */
    private static void testConcurrentMap() throws InterruptedException {
        try (MVStore s = openStore(null)) {
            final MVMap<Integer, Integer> m = s.openMap("data");
            final int size = 20;
            final Random rand = new Random(1);
            Task task = new Task() {
                @Override
                public void call() {
                    try {
                        while (!stop) {
                            if (rand.nextBoolean()) {
                                m.put(rand.nextInt(size), 1);
                            } else {
                                m.remove(rand.nextInt(size));
                            }
                            m.get(rand.nextInt(size));
                            m.firstKey();
                            m.lastKey();
                            m.ceilingKey(5);
                            m.floorKey(5);
                            m.higherKey(5);
                            m.lowerKey(5);
                            for (Iterator<Integer> it = m.keyIterator(null);
                                    it.hasNext();) {
                                it.next();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            task.execute();
            Thread.sleep(1);
            for (int j = 0; j < 100; j++) {
                for (int i = 0; i < 100; i++) {
                    if (rand.nextBoolean()) {
                        m.put(rand.nextInt(size), 2);
                    } else {
                        m.remove(rand.nextInt(size));
                    }
                    m.get(rand.nextInt(size));
                }
                s.commit();
                Thread.sleep(1);
            }
            task.get();
        }
    }

    private void testConcurrentOnlineBackup() throws Exception {
        String fileName = getBaseDir() + "/" + getTestName();
        String fileNameRestore = getBaseDir() + "/" + getTestName() + ".bck";
        FileUtils.delete(fileName);
        FileUtils.delete(fileNameRestore);
        try (final MVStore s = openStore(fileName)) {
            final MVMap<Integer, byte[]> map = s.openMap("test");
            final Random r = new Random();
            Task task = new Task() {
                @Override
                public void call() throws Exception {
                    while (!stop) {
                        for (int i = 0; i < 10; i++) {
                            map.put(i, new byte[100 * r.nextInt(100)]);
                        }
                        s.commit();
                        map.clear();
                        s.commit();
                        long len = s.getFileStore().size();
                        if (len > 1024 * 1024) {
                            // slow down writing a lot
                            Thread.sleep(200);
                        } else if (len > 20 * 1024) {
                            // slow down writing
                            Thread.sleep(20);
                        }
                    }
                }
            };
            task.execute();
            try {
                String archiveName = fileNameRestore + ".zip";
                for (int i = 0; i < 10; i++) {
                    FileUtils.delete(archiveName);
                    try (OutputStream out = FileUtils.newOutputStream(archiveName, false)) {
                        try (ZipOutputStream zip = new ZipOutputStream(out)) {
                            s.getFileStore().backup(zip);
                        }
                    }

                    try (ZipFile zipFile = new ZipFile(archiveName)) {
                        String name = FilePath.get(s.getFileStore().getFileName()).getName();
                        ZipEntry zipEntry = zipFile.getEntry(name);
                        try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                            try (OutputStream out = FilePath.get(fileNameRestore).newOutputStream(false)) {
                                IOUtils.copy(inputStream, out);
                            }
                        }
                    }

                    try (MVStore s2 = openStore(fileNameRestore)) {
                        MVMap<Integer, byte[]> test = s2.openMap("test");
                        for (Integer k : test.keySet()) {
                            test.get(k);
                        }
                    }
                    // let it compact
                    Thread.sleep(10);
                }
            } finally {
                task.get();
            }
        }
    }

    private static void testConcurrentIterate() {
        try (MVStore s = new MVStore.Builder().pageSplitSize(3).open()) {
            s.setVersionsToKeep(100);
            final MVMap<Integer, Integer> map = s.openMap("test");
            final int len = 10;
            final Random r = new Random();
            Task task = new Task() {
                @Override
                public void call() {
                    while (!stop) {
                        int x = r.nextInt(len);
                        if (r.nextBoolean()) {
                            map.remove(x);
                        } else {
                            map.put(x, r.nextInt(100));
                        }
                    }
                }
            };
            task.execute();
            try {
                for (int k = 0; k < 10000; k++) {
                    Iterator<Integer> it = map.keyIterator(r.nextInt(len));
                    long old = map.getVersion();
                    s.commit();
                    while (map.getVersion() == old) {
                        Thread.yield();
                    }
                    while (it.hasNext()) {
                        it.next();
                    }
                }
            } finally {
                task.get();
            }
        }
    }


    /**
     * Test what happens on concurrent write. Concurrent write may corrupt the
     * map, so that keys and values may become null.
     */
    private void testConcurrentWrite() throws InterruptedException {
        final AtomicInteger detected = new AtomicInteger();
        final AtomicInteger notDetected = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            testConcurrentWrite(detected, notDetected);
        }
        // in most cases, it should be detected
        assertTrue(notDetected.get() * 10 <= detected.get());
    }

    private static void testConcurrentWrite(final AtomicInteger detected,
            final AtomicInteger notDetected) throws InterruptedException {
        try (final MVStore s = openStore(null)) {
            final MVMap<Integer, Integer> m = s.openMap("data");
            final int size = 20;
            final Random rand = new Random(1);
            Task task = new Task() {
                @Override
                public void call() {
                    while (!stop) {
                        try {
                            if (rand.nextBoolean()) {
                                m.put(rand.nextInt(size), 1);
                            } else {
                                m.remove(rand.nextInt(size));
                            }
                            m.get(rand.nextInt(size));
                        } catch (ConcurrentModificationException e) {
                            detected.incrementAndGet();
                        } catch (NegativeArraySizeException
                                | ArrayIndexOutOfBoundsException
                                | IllegalArgumentException
                                | NullPointerException e) {
                            notDetected.incrementAndGet();
                        }
                    }
                }
            };
            task.execute();
            try {
                Thread.sleep(1);
                for (int j = 0; j < 10; j++) {
                    for (int i = 0; i < 10; i++) {
                        try {
                            if (rand.nextBoolean()) {
                                m.put(rand.nextInt(size), 2);
                            } else {
                                m.remove(rand.nextInt(size));
                            }
                            m.get(rand.nextInt(size));
                        } catch (ConcurrentModificationException e) {
                            detected.incrementAndGet();
                        } catch (NegativeArraySizeException
                                | ArrayIndexOutOfBoundsException
                                | NullPointerException
                                | IllegalArgumentException e) {
                            notDetected.incrementAndGet();
                        }
                    }
                    s.commit();
                    Thread.sleep(1);
                }
            } finally {
                task.get();
            }
        }
    }

    private static void testConcurrentRead() throws InterruptedException {
        try (final MVStore s = openStore(null)) {
            s.setVersionsToKeep(100);
            final MVMap<Integer, Integer> m = s.openMap("data");
            final int size = 3;
            int x = (int) s.getCurrentVersion();
            for (int i = 0; i < size; i++) {
                m.put(i, x);
            }
            s.commit();
            Task task = new Task() {
                @Override
                public void call() {
                    while (!stop) {
                        long v = s.getCurrentVersion() - 1;
                        Map<Integer, Integer> old = m.openVersion(v);
                        for (int i = 0; i < size; i++) {
                            Integer x = old.get(i);
                            if (x == null || (int) v != x) {
                                Map<Integer, Integer> old2 = m.openVersion(v);
                                throw new AssertionError(x + "<>" + v + " at " + i + " " + old2);
                            }
                        }
                    }
                }
            };
            task.execute();
            try {
                Thread.sleep(1);
                for (int j = 0; j < 100; j++) {
                    x = (int) s.getCurrentVersion();
                    for (int i = 0; i < size; i++) {
                        m.put(i, x);
                    }
                    s.commit();
                    Thread.sleep(1);
                }
            } finally {
                task.get();
            }
        }
    }
}
