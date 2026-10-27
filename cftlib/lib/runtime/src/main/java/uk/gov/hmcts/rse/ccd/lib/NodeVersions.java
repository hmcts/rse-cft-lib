package uk.gov.hmcts.rse.ccd.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compares the host's node against the version range an XUI app declares in its package.json.
 *
 * <p>Only the comparator syntax XUI actually uses is supported: a space separated list of
 * {@code >=}, {@code >}, {@code <=}, {@code <} and {@code =} clauses, each with a possibly
 * partial version, all of which must hold.
 */
final class NodeVersions {

    private static final Pattern CLAUSE = Pattern.compile("^(>=|<=|>|<|=)?\\s*v?(\\d+(?:\\.\\d+){0,2})$");
    private static final Pattern ENGINES_NODE = Pattern.compile(
        "\"engines\"\\s*:\\s*\\{[^}]*?\"node\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);

    private NodeVersions() {
    }

    static boolean satisfies(String version, String range) {
        var found = parse(version);
        // An operator may be written apart from its version, as in '>= 24.22.1'.
        var clauses = range.trim().replaceAll("(>=|<=|>|<|=)\\s+", "$1");
        for (var clause : clauses.split("\\s*,\\s*|\\s+")) {
            if (!clause.isEmpty() && !satisfiesClause(found, clause)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Throws with both the required and the found version, which is the only actionable thing we
     * can tell an engineer whose node is too old.
     */
    static void require(String app, String range, String version) {
        if (!satisfies(version, range)) {
            throw new IllegalStateException(
                app + " requires node " + range + " but found " + version + " on the path. "
                    + "Install a matching node, or run XUI in docker with RSE_LIB_XUI=docker.");
        }
    }

    /**
     * Reads the {@code engines.node} range out of a package.json, which is authoritative for the
     * checked out revision.
     */
    static String enginesNode(String packageJson) {
        var matcher = ENGINES_NODE.matcher(packageJson);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    /**
     * The version that ends the range a partial version admits: 24 is bounded by 25, 24.22 by
     * 24.23.
     */
    private static List<Integer> nextOf(List<Integer> declared) {
        var next = new ArrayList<>(declared);
        next.set(next.size() - 1, next.get(next.size() - 1) + 1);
        return next;
    }

    private static boolean satisfiesClause(List<Integer> found, String clause) {
        var matcher = CLAUSE.matcher(clause.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported node version range clause: " + clause);
        }
        var declared = parse(matcher.group(2));
        var operator = matcher.group(1) == null ? "=" : matcher.group(1);
        if (declared.size() < 3) {
            // A partial version stands for a range, so npm moves the bound to the end of it:
            // '24' admits every 24.x, '>24' means after all of them, '<=24' through all of them.
            return switch (operator) {
                case ">=" -> compare(found, declared) >= 0;
                case ">" -> compare(found, nextOf(declared)) >= 0;
                case "<=" -> compare(found, nextOf(declared)) < 0;
                case "<" -> compare(found, declared) < 0;
                default -> compare(found, declared) >= 0 && compare(found, nextOf(declared)) < 0;
            };
        }
        var comparison = compare(found, declared);
        return switch (operator) {
            case ">=" -> comparison >= 0;
            case ">" -> comparison > 0;
            case "<=" -> comparison <= 0;
            case "<" -> comparison < 0;
            default -> comparison == 0;
        };
    }

    private static int compare(List<Integer> left, List<Integer> right) {
        for (var i = 0; i < 3; i++) {
            var result = Integer.compare(left.get(i), right.get(i));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /**
     * Parses a possibly partial version, padding the missing components with zero so that
     * {@code <25} means {@code <25.0.0}.
     */
    private static List<Integer> parse(String version) {
        var trimmed = version.trim();
        if (trimmed.startsWith("v")) {
            trimmed = trimmed.substring(1);
        }
        // node --version can report a pre-release, eg. v25.0.0-nightly.
        var dash = trimmed.indexOf('-');
        if (dash != -1) {
            trimmed = trimmed.substring(0, dash);
        }
        var parts = new ArrayList<Integer>(List.of(0, 0, 0));
        var fields = trimmed.split("\\.");
        for (var i = 0; i < fields.length && i < 3; i++) {
            try {
                parts.set(i, Integer.parseInt(fields[i]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unsupported node version: " + version, e);
            }
        }
        return parts;
    }
}
