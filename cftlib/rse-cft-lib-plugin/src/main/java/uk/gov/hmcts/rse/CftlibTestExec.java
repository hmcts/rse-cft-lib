package uk.gov.hmcts.rse;

import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.options.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CftlibTestExec extends CftlibExec {
    private final List<String> testPatterns = new ArrayList<>();

    @Option(option = "tests", description = "Run matching test classes (simple or fully qualified names; supports *).")
    public void setTests(List<String> patterns) {
        testPatterns.clear();
        testPatterns.addAll(patterns);
    }

    @Internal
    public List<String> getTestArguments() {
        var args = new ArrayList<>(List.of("execute", "--select-package=uk.gov.hmcts"));
        if (!testPatterns.isEmpty()) {
            String expression = testPatterns.stream().map(pattern -> {
                String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
                // A simple class name should match in any package.
                return pattern.contains(".") ? regex : "(?:.*\\.)?" + regex;
            }).collect(Collectors.joining("|"));
            args.add("--include-classname=" + expression);
            args.add("--fail-if-no-tests");
        }
        return args;
    }
}
