// FILE: test.kt

class A {
    companion object {
        val s: String

        init {
            s = "OK"
        }

        val x = x()

        init { val a = 32 }

        init {
            val b = 42
        }

        init
        {
            val c = 43
        }
    }
}

fun x() = ""

fun box() {
    A.x
    A.s
}

// JVM backend has extra steps for getX and getS.

// TODO: since these tests operate as step-into, we do not get any steps
// in the <clinit> for the companion object. Maybe extend the test infrastructure
// with directives to set break points in order to test this.

// LINENUMBERS
// test.kt:29 box
// test.kt:7 <clinit>
// test.kt:8 <clinit>
// test.kt:9 <clinit>
// test.kt:11 <clinit>
// test.kt:13 <clinit>
// test.kt:15 <clinit>
// test.kt:16 <clinit>
// test.kt:17 <clinit>
// test.kt:19 <clinit>
// test.kt:21 <clinit>
// test.kt:22 <clinit>
// test.kt:11 getX
// LINENUMBERS JVM
// test.kt:11 getX
// LINENUMBERS
// test.kt:29 box
// test.kt:30 box
// test.kt:5 getS
// LINENUMBERS JVM
// test.kt:5 getS
// LINENUMBERS
// test.kt:30 box
// test.kt:31 box