// Local-only stand-in for JUnit 4 (no Maven access in the dev sandbox). Not part of the app build.
package org.junit
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Test
