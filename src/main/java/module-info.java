/**
* Module descriptor for {@code boraheeb.util.logging}.
*
* <p>
*   The library itself has no dependencies beyond {@code java.base}. {@code java.management}
*   is required only because Maven compiles this project's test-scope benchmark
*   harnesses (which report GC/heap stats via {@code java.lang.management}) against
*   this same module; it is a standard module bundled with every JDK, not a real
*   external dependency, and no library code under {@code boraheeb.util.logging} uses it.
* </p>
*
* <p>The single package is exported unconditionally, since the whole public API lives in it.</p>
**/
module boraheeb.util.logging{
    requires java.management;
    exports boraheeb.util.logging;
}