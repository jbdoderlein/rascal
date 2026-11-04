package org.rascalmpl.uri.libraries;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.rascalmpl.uri.FileAttributes;
import org.rascalmpl.uri.ISourceLocationInput;

import io.usethesource.vallang.ISourceLocation;

/**
 * |resource:///path| reads `path` from the class path, wherever it is (a folder, a jar, or the
 * browser's virtual file system under CheerpJ). For example, a parser stored at build time with
 * `storeParsers` and packed into a program's jar can be loaded with `loadParsers(|resource:///E.parsers|)`.
 *
 * Resources are read-only files; directories can not be listed.
 */
public class ClasspathResourceResolver implements ISourceLocationInput {

    private static String path(ISourceLocation uri) {
        var path = uri.getPath();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private static ClassLoader loader() {
        var loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : ClasspathResourceResolver.class.getClassLoader();
    }

    @Override
    public String scheme() {
        return "resource";
    }

    @Override
    public InputStream getInputStream(ISourceLocation uri) throws IOException {
        var stream = loader().getResourceAsStream(path(uri));
        if (stream == null) {
            throw new FileNotFoundException(uri.toString());
        }
        return stream;
    }

    @Override
    public boolean exists(ISourceLocation uri) {
        return loader().getResource(path(uri)) != null;
    }

    @Override
    public boolean isFile(ISourceLocation uri) {
        return exists(uri);
    }

    @Override
    public boolean isDirectory(ISourceLocation uri) {
        return false;
    }

    @Override
    public boolean isReadable(ISourceLocation uri) throws IOException {
        return exists(uri);
    }

    @Override
    public long lastModified(ISourceLocation uri) throws IOException {
        return 0L;
    }

    @Override
    public long size(ISourceLocation uri) throws IOException {
        try (var in = getInputStream(uri)) {
            return in.transferTo(OutputStream.nullOutputStream());
        }
    }

    @Override
    public Charset getCharset(ISourceLocation uri) throws IOException {
        return null;
    }

    @Override
    public String[] list(ISourceLocation uri) throws IOException {
        return new String[0];
    }

    @Override
    public boolean supportsHost() {
        return false;
    }

    @Override
    public FileAttributes stat(ISourceLocation uri) throws IOException {
        var exists = exists(uri);
        return new FileAttributes(exists, exists, 0L, 0L, exists, false, exists ? size(uri) : 0L);
    }
}
