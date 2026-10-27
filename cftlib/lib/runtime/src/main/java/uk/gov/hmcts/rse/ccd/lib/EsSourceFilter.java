package uk.gov.hmcts.rse.ccd.lib;

import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Applies a search's _source includes to a document, as elasticsearch does before returning a hit.
 *
 * <p>CCD always sends an includes list, and relies on it: the metadata it asks for omits the
 * document's internal id, so a hit returned by a real elasticsearch carries no id. Reproducing the
 * filtering keeps the stub's results the same shape as production's.
 */
final class EsSourceFilter {

    private EsSourceFilter() {
    }

    static JsonNode apply(JsonNode document, List<String> includes) {
        if (includes.isEmpty()) {
            return document;
        }
        var patterns = includes.stream().map(EsSourceFilter::toPattern).toList();
        var filtered = filter(document, "", patterns);
        return filtered == null ? JsonNodeFactory.instance.objectNode() : filtered;
    }

    private static JsonNode filter(JsonNode node, String path, List<Pattern> includes) {
        if (!path.isEmpty() && includes.stream().anyMatch(p -> p.matcher(path).matches())) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode kept = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(entry -> {
                var child = filter(entry.getValue(),
                    path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), includes);
                if (child != null) {
                    kept.set(entry.getKey(), child);
                }
            });
            return kept.isEmpty() ? null : kept;
        }
        if (node.isArray()) {
            var kept = JsonNodeFactory.instance.arrayNode();
            node.forEach(element -> {
                var child = filter(element, path, includes);
                if (child != null) {
                    kept.add(child);
                }
            });
            return kept.isEmpty() ? null : kept;
        }
        return null;
    }

    /**
     * Elasticsearch matches include patterns against the whole dotted path, with * matching any
     * run of characters.
     */
    private static Pattern toPattern(String include) {
        var regex = new StringBuilder();
        var first = true;
        for (var part : include.split("\\*", -1)) {
            if (!first) {
                regex.append(".*");
            }
            first = false;
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }
}
