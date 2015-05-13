package com.strikew.conformance;

import com.strikew.test.*;

/** Runs all conformance tests on distributed filesystem components.

    <p>
    Tests performed are:
    <ul>
    <li>{@link com.strikew.conformance.rmi.SkeletonTest}</li>
    <li>{@link com.strikew.conformance.rmi.StubTest}</li>
    <li>{@link com.strikew.conformance.rmi.ConnectionTest}</li>
    <li>{@link com.strikew.conformance.rmi.ThreadTest}</li>
    </ul>
 */
public class ConformanceTests
{
    /** Runs the tests.

        @param arguments Ignored.
     */
    public static void main(String[] arguments)
    {
        // Create the test list, the series object, and run the test series.
        @SuppressWarnings("unchecked")
        Class<? extends Test>[]     tests =
            new Class[] { com.strikew.conformance.rmi.SkeletonTest.class,
                         com.strikew.conformance.rmi.StubTest.class,
                         com.strikew.conformance.rmi.ConnectionTest.class,
                         com.strikew.conformance.rmi.ThreadTest.class};
        Series                      series = new Series(tests);
        SeriesReport                report = series.run(3, System.out);

        // Print the report and exit with an appropriate exit status.
        report.print(System.out);
        System.exit(report.successful() ? 0 : 2);
    }
}
