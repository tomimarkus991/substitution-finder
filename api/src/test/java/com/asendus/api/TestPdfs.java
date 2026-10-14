package com.asendus.api;

import java.io.IOException;
import java.io.InputStream;

public final class TestPdfs {
    private TestPdfs() {}

    public static byte[] load(String name) {
        try (InputStream in = TestPdfs.class.getResourceAsStream("/pdfs/" + name)) {
            if (in == null) throw new IllegalArgumentException("No test PDF " + name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
