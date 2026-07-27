package com.donohoedigital.ddphotos.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.ReadOnlyFileSystemException;

import static org.junit.Assert.*;

/**
 * Unit tests for the file-failure explanations.  The interesting cases are the NIO exceptions
 * whose own {@code getMessage()} is nothing but the path.
 */
public class FileErrorsTest {

    private static final String PATH = "/Users/donohoe/my-ddphotos/config/albums.yaml";

    // ── rootCause ───────────────────────────────────────────────────────────

    @Test
    public void rootCause_unwrapsTheChain() {
        IOException io = new NoSuchFileException(PATH);
        Throwable wrapped = new AlbumsFileException("write " + PATH, io);
        assertSame(io, FileErrors.rootCause(wrapped));
    }

    @Test
    public void rootCause_returnsSelfWhenNoCause() {
        Throwable e = new AlbumsFileException("album[0]: slug is required");
        assertSame(e, FileErrors.rootCause(e));
    }

    @Test
    public void rootCause_survivesASelfReferencingCause() {
        // Not reachable through initCause(), but a hand-rolled getCause() can do it.
        Throwable looping = new RuntimeException() {
            @Override public synchronized Throwable getCause() { return this; }
        };
        assertSame(looping, FileErrors.rootCause(looping));
    }

    // ── reason ──────────────────────────────────────────────────────────────

    @Test
    public void reason_describesTheNioExceptions() {
        assertEquals("no such file or directory", FileErrors.reason(new NoSuchFileException(PATH)));
        assertEquals("permission denied",         FileErrors.reason(new AccessDeniedException(PATH)));
        assertEquals("not a directory",           FileErrors.reason(new NotDirectoryException(PATH)));
        assertEquals("read-only file system",     FileErrors.reason(new ReadOnlyFileSystemException()));
    }

    @Test
    public void reason_looksThroughTheWrapper() {
        // The bug this class exists for: getMessage() on the cause is just the path again.
        AlbumsFileException e = new AlbumsFileException("write " + PATH, new NoSuchFileException(PATH));
        assertEquals("no such file or directory", FileErrors.reason(e));
    }

    @Test
    public void reason_usesTheOsReasonWhenThereIsOne() {
        assertEquals("No space left on device",
                FileErrors.reason(new FileSystemException(PATH, null, "No space left on device")));
    }

    @Test
    public void reason_fallsBackToClassNameForABareFileSystemException() {
        // getMessage() here is only the path, which the caller has already printed.
        assertEquals("FileSystemException", FileErrors.reason(new FileSystemException(PATH)));
    }

    @Test
    public void reason_usesTheMessageOfOtherExceptions() {
        assertEquals("stream closed", FileErrors.reason(new IOException("stream closed")));
        assertEquals("album[0]: slug is required",
                FileErrors.reason(new AlbumsFileException("album[0]: slug is required")));
    }

    @Test
    public void reason_fallsBackToClassNameWhenThereIsNoMessage() {
        assertEquals("NullPointerException", FileErrors.reason(new NullPointerException()));
        assertEquals("IOException", FileErrors.reason(new IOException("  ")));
    }

    // ── messageKey ──────────────────────────────────────────────────────────

    @Test
    public void messageKey_mapsTheExplainableCauses() {
        assertEquals("msg.error.file.missing",
                FileErrors.messageKey(new AlbumsFileException("write", new NoSuchFileException(PATH))));
        assertEquals("msg.error.file.denied",      FileErrors.messageKey(new AccessDeniedException(PATH)));
        assertEquals("msg.error.file.notdirectory", FileErrors.messageKey(new NotDirectoryException(PATH)));
        assertEquals("msg.error.file.readonly",    FileErrors.messageKey(new ReadOnlyFileSystemException()));
    }

    @Test
    public void messageKey_isNullWhenTheExceptionSpeaksForItself() {
        assertNull(FileErrors.messageKey(new AlbumsFileException("album[0]: slug is required")));
        assertNull(FileErrors.messageKey(new FileSystemException(PATH, null, "No space left on device")));
    }

    // ── isIoFailure ─────────────────────────────────────────────────────────

    @Test
    public void isIoFailure_distinguishesIoFromValidation() {
        assertTrue(FileErrors.isIoFailure(new AlbumsFileException("write", new NoSuchFileException(PATH))));
        assertTrue(FileErrors.isIoFailure(new ReadOnlyFileSystemException()));
        assertFalse(FileErrors.isIoFailure(new AlbumsFileException("album[0]: slug is required")));
    }
}
