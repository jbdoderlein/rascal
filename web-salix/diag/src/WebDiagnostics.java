import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

import org.rascalmpl.values.ValueFactoryFactory;

/**
 * Runs the operations suspected to fail under CheerpJ one by one, and reports which ones throw.
 * (CheerpJ's Java exceptions have no stack traces, so this is how to localize a failure.)
 *
 * From the browser console, on the Salix page:
 *   const lib = await cheerpjRunLibrary("/app/diag.jar:/app/app.jar:/app/rascal.jar");
 *   console.log(await (await lib.WebDiagnostics).run());
 */
public class WebDiagnostics {
    private static final StringBuilder report = new StringBuilder();

    private static void step(String name, Callable<Object> body) {
        try {
            report.append("ok    ").append(name).append(" -> ").append(body.call()).append('\n');
        }
        catch (Throwable e) {
            report.append("FAIL  ").append(name).append(" -> ").append(e).append('\n');
            // (empty under CheerpJ, which has no Java stack traces)
            StackTraceElement[] trace = e.getStackTrace();
            for (int i = 0; i < Math.min(trace.length, 6); i++) {
                report.append("        at ").append(trace[i]).append('\n');
            }
            if (trace.length > 6) {
                report.append("        ... (").append(trace.length).append(" frames; where it started:)\n");
                for (int i = Math.max(6, trace.length - 30); i < trace.length; i++) {
                    report.append("        at ").append(trace[i]).append('\n');
                }
            }
        }
    }

    private static Cache<String, String> fill(Cache<String, String> cache) {
        cache.put("a", "A");
        return cache;
    }

    public static String run() {
        report.setLength(0);

        // Caffeine, from the simplest cache to the configurations vallang uses
        step("ConcurrentHashMap get", () -> new ConcurrentHashMap<>(java.util.Map.of("a", "A")).get("a"));
        step("Caffeine unbounded: miss", () -> Caffeine.newBuilder().build().get("a", k -> "A"));
        step("Caffeine unbounded: hit", () -> fill(Caffeine.newBuilder().<String, String>build()).getIfPresent("a"));
        step("Caffeine maximumSize: miss", () -> Caffeine.newBuilder().maximumSize(1000).build().get("a", k -> "A"));
        step("Caffeine maximumSize: hit", () -> fill(Caffeine.newBuilder().maximumSize(1000).<String, String>build()).getIfPresent("a"));
        step("Caffeine expireAfterAccess: hit", () -> fill(Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).<String, String>build()).getIfPresent("a"));
        step("Caffeine maximumSize+expireAfterAccess: hit", () -> fill(Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).maximumSize(1000).<String, String>build()).getIfPresent("a"));
        step("Caffeine maximumSize+expireAfterAccess: get twice", () -> {
            Cache<String, String> c = Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).maximumSize(1000).build();
            c.get("a", k -> "A");
            return c.get("a", k -> "B");
        });
        step("Caffeine weakKeys: hit", () -> {
            Cache<String, String> c = Caffeine.newBuilder().weakKeys().build();
            String key = new String("a");
            c.put(key, "A");
            return c.getIfPresent(key);
        });
        step("Caffeine softValues: hit", () -> fill(Caffeine.newBuilder().softValues().<String, String>build()).getIfPresent("a"));

        // vallang source locations, whose getURI() goes through a Caffeine cache
        IValueFactory vf = ValueFactoryFactory.getValueFactory();
        step("location getURI (1st)", () -> vf.sourceLocation(URI.create("prompt:///diag")).getURI());
        step("location getURI (2nd, cache hit)", () -> vf.sourceLocation(URI.create("prompt:///diag")).getURI());
        step("location with offsets getURI twice", () -> {
            ISourceLocation l = vf.sourceLocation(vf.sourceLocation(URI.create("prompt:///diag2")), 0, 5, 1, 1, 0, 5);
            l.getURI();
            return l.getURI();
        });
        // the same, split into its parts
        step("root location hashCode", () -> vf.sourceLocation(URI.create("prompt:///diag3")).hashCode());
        step("create location with offsets", () -> vf.sourceLocation(vf.sourceLocation(URI.create("prompt:///diag4")), 0, 5, 1, 1, 0, 5).getClass().getSimpleName());
        step("location with offsets hashCode", () -> vf.sourceLocation(vf.sourceLocation(URI.create("prompt:///diag5")), 0, 5, 1, 1, 0, 5).hashCode());
        step("location with offsets getURI once", () -> vf.sourceLocation(vf.sourceLocation(URI.create("prompt:///diag6")), 0, 5, 1, 1, 0, 5).getURI());

        // a bounded Caffeine cache indexes a long[] frequency sketch with a mix of the key's hash
        for (int key : new int[] { 97, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 123456789, -987654321 }) {
            step("Caffeine maximumSize hits, key hash " + key, () -> {
                Cache<Integer, String> c = Caffeine.newBuilder().maximumSize(1000).build();
                c.put(key, "A");
                String r = null;
                for (int i = 0; i < 20; i++) {
                    r = c.getIfPresent(key);
                }
                return r;
            });
        }

        // the arithmetic Caffeine's FrequencySketch uses; compare with the JVM's output
        step("arith: -1 >>> 1", () -> -1 >>> 1);
        step("arith: MIN_VALUE >>> 28", () -> Integer.MIN_VALUE >>> 28);
        step("arith: (long) -987654321 * 0x9E3779B97F4A7C15L", () -> (long) -987654321 * 0x9E3779B97F4A7C15L);
        step("arith: long >>> 32 of that", () -> ((long) -987654321 * 0x9E3779B97F4A7C15L) >>> 32);
        step("arith: (int) (long >>> 32)", () -> (int) (((long) -987654321 * 0x9E3779B97F4A7C15L) >>> 32));
        step("arith: -987654321 * 0x45d9f3b", () -> -987654321 * 0x45d9f3b);
        step("arith: Long.rotateLeft(-5L, 7)", () -> Long.rotateLeft(-5L, 7));
        step("arith: Integer.rotateLeft(-5, 7)", () -> Integer.rotateLeft(-5, 7));
        step("arith: (-987654321 ^ (-987654321 >>> 16)) & 1023", () -> (-987654321 ^ (-987654321 >>> 16)) & 1023);
        step("arith: Long.numberOfTrailingZeros(64)", () -> Long.numberOfTrailingZeros(64));
        step("arith: (int) 0xFFFFFFFFL", () -> (int) 0xFFFFFFFFL);

        // vallang types: a type parameter inside a function type (what failed in the interpreter)
        TypeFactory tf = TypeFactory.getInstance();
        step("function type with type parameter", () -> {
            Type t = tf.parameterType("T");
            return tf.functionType(tf.voidType(), tf.tupleType(t), tf.tupleEmpty());
        });
        step("match void(&T) against void(int)", () -> {
            Type t = tf.parameterType("T");
            Type formal = tf.functionType(tf.voidType(), tf.tupleType(t), tf.tupleEmpty());
            Type actual = tf.functionType(tf.voidType(), tf.tupleType(tf.integerType()), tf.tupleEmpty());
            var bindings = new java.util.HashMap<Type, Type>();
            return formal.match(actual, bindings) + " " + bindings;
        });
        step("instantiate void(&T) with T=int", () -> {
            Type t = tf.parameterType("T");
            Type formal = tf.functionType(tf.voidType(), tf.tupleType(t), tf.tupleEmpty());
            return formal.instantiate(java.util.Map.of(t, tf.integerType()));
        });
        step("reify a function type to a value", () -> {
            Type t = tf.parameterType("T");
            Type formal = tf.functionType(tf.voidType(), tf.tupleType(t), tf.tupleEmpty());
            return new org.rascalmpl.types.TypeReifier(vf).typeToValue(formal, new TypeStore(), vf.map());
        });

        // hot loops: a JIT that miscompiles after warm-up would show up as mismatches here
        for (int round = 1; round <= 3; round++) {
            int r = round;
            step("round " + r + ": Integer.bitCount mismatches in 200000", () -> {
                int bad = 0, x = 12345 * r;
                for (int i = 0; i < 200000; i++) {
                    x = x * 1103515245 + 12345;
                    int naive = 0;
                    for (int b = 0; b < 32; b++) {
                        naive += (x >>> b) & 1;
                    }
                    if (Integer.bitCount(x) != naive) {
                        bad++;
                    }
                }
                return bad;
            });
            step("round " + r + ": CHAMP-style index mismatches in 200000", () -> {
                int bad = 0, x = 777 * r;
                for (int i = 0; i < 200000; i++) {
                    x = x * 1103515245 + 12345;
                    int shift = (i % 7) * 5;
                    int mask = (x >>> shift) & 0b11111;
                    int bitpos = 1 << mask;
                    int bitmap = x | bitpos;
                    int index = Integer.bitCount(bitmap & (bitpos - 1));
                    int naive = 0;
                    for (int b = 0; b < mask; b++) {
                        naive += (bitmap >>> b) & 1;
                    }
                    if (index != naive || mask != (int) ((((long) x) & 0xFFFFFFFFL) / (1L << shift) % 32)) {
                        bad++;
                    }
                }
                return bad;
            });
            step("round " + r + ": capsule map, 20000 entries, wrong gets", () -> {
                io.usethesource.capsule.Map.Transient<Integer, Integer> m = io.usethesource.capsule.Map.Transient.of();
                int x = 99 * r;
                int[] keys = new int[20000];
                for (int i = 0; i < keys.length; i++) {
                    x = x * 1103515245 + 12345;
                    keys[i] = x;
                    m.__put(x, i);
                }
                int bad = 0;
                for (int i = 0; i < keys.length; i++) {
                    Integer v = m.get(keys[i]);
                    // (later duplicates of a key overwrite earlier ones)
                    if (v == null || keys[v] != keys[i]) {
                        bad++;
                    }
                }
                return bad + " (size " + m.size() + ")";
            });
            step("round " + r + ": vallang map, 20000 entries, wrong gets", () -> {
                var w = vf.mapWriter();
                for (int i = 0; i < 20000; i++) {
                    w.put(vf.integer(i * 7919), vf.integer(i));
                }
                var m = w.done();
                int bad = 0;
                for (int i = 0; i < 20000; i++) {
                    var v = m.get(vf.integer(i * 7919));
                    if (v == null || !v.equals(vf.integer(i))) {
                        bad++;
                    }
                }
                return bad;
            });
            step("round " + r + ": 2000 locations with offsets, getURI twice", () -> {
                var root = vf.sourceLocation(URI.create("prompt:///round" + r));
                for (int i = 0; i < 2000; i++) {
                    var l = vf.sourceLocation(root, i, 5, 1 + i % 100, 1 + i % 100, 0, 5);
                    l.getURI();
                    l.getURI();
                }
                return "done";
            });
            step("round " + r + ": reify a function type to a value", () -> {
                Type t = tf.parameterType("T");
                Type formal = tf.functionType(tf.voidType(), tf.tupleType(t), tf.tupleEmpty());
                return new org.rascalmpl.types.TypeReifier(vf).typeToValue(formal, new TypeStore(), vf.map());
            });
        }

        return report.toString();
    }

    public static void main(String[] args) {
        System.out.print(run());
    }
}
