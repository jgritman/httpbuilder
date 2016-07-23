package groovyx.net.http;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Traverser {

    public static <T,V> V traverse(final T target, final Function<T,T> next,
                                   final Function<T,V> getValue, final Predicate<V> testValue) {
        final V v = getValue.apply(target);
        if(testValue.test(v)) {
            return v;
        }

        final T nextTarget = next.apply(target);
        if(nextTarget != null) {
            return traverse(nextTarget, next, getValue, testValue);
        }

        return null;
    }

    public static <V> boolean notNull(final V v) { return v != null; }
    public static <V> Predicate<V> notValue(final V v) { return (toTest) -> !v.equals(toTest); }
}
