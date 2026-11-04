package org.rascalmpl.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;

/**
 * Loads a parser stored by `storeParsers`: a jar whose entries are the parser's class files, named
 * by their qualified class names, and whose Main-Class is the parser itself.
 *
 * Unlike the interpreter's JavaBridge, this needs no Java compiler, so compiled Rascal programs can
 * use stored parsers where javac is not available (e.g. a slim runtime, or the browser).
 */
class StoredParserClassLoader extends ClassLoader {
    private final Map<String, byte[]> classes = new HashMap<>();
    private final String mainClass;

    private StoredParserClassLoader(ClassLoader parent, InputStream in) throws IOException {
        super(parent);
        try (var jar = new JarInputStream(in)) {
            var manifest = jar.getManifest();
            mainClass = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (mainClass == null) {
                throw new IOException("missing Main-Class in the manifest of the stored parser");
            }
            for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                if (!entry.isDirectory()) {
                    classes.put(entry.getName(), jar.readAllBytes());
                }
            }
        }
    }

    static Class<?> loadParserClass(ClassLoader parent, InputStream in) throws IOException, ClassNotFoundException {
        var loader = new StoredParserClassLoader(parent, in);
        return loader.loadClass(loader.mainClass);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        var bytes = classes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
