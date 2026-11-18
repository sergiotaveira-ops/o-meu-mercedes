// Local-only test runner: runs every @org.junit.Test method in the given classes.
import java.lang.reflect.InvocationTargetException
fun main(args: Array<String>) {
    var pass = 0; var fail = 0
    for (cn in args) {
        val c = Class.forName(cn)
        for (m in c.methods.filter { it.isAnnotationPresent(org.junit.Test::class.java) }.sortedBy { it.name }) {
            try { m.invoke(c.getDeclaredConstructor().newInstance()); pass++; println("PASS ${c.simpleName}.${m.name}") }
            catch (e: InvocationTargetException) { fail++; println("FAIL ${c.simpleName}.${m.name}: ${e.targetException}") }
        }
    }
    println("RESULT: $pass passed, $fail failed")
    if (fail > 0 || pass == 0) kotlin.system.exitProcess(1)
}
