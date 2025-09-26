package com.evalscope.fasthttp.http;

import java.util.*;

public class Headers implements Iterable<Map.Entry<String, List<String>>> {
    private final Map<String, List<String>> headers;

    Headers() {
        this.headers = new HashMap<>();
    }

    Headers(Map<String, List<String>> headers) {
        this.headers = new HashMap<>(headers);
    }

    Headers(Headers headers) {
        this.headers = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.headers.entrySet()) {
            this.headers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    public String get(String name) {
        List<String> values = headers.get(normalizeName(name));
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public List<String> values(String name) {
        List<String> values = headers.get(normalizeName(name));
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(headers.keySet());
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    private String normalizeName(String name) {
        Objects.requireNonNull(name, "name == null");
        return name.trim().toLowerCase();
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return headers.entrySet().iterator();
    }

    public static class Builder {
        private final Map<String, List<String>> headers = new HashMap<>();

        Builder() {}

        Builder(Headers headers) {
            for (Map.Entry<String, List<String>> entry : headers) {
                this.headers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        public Builder add(String name, String value) {
            Objects.requireNonNull(name, "name == null");
            Objects.requireNonNull(value, "value == null");
            String normalizedName = name.trim().toLowerCase();
            headers.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder set(String name, String value) {
            Objects.requireNonNull(name, "name == null");
            Objects.requireNonNull(value, "value == null");
            String normalizedName = name.trim().toLowerCase();
            headers.put(normalizedName, new ArrayList<>(Arrays.asList(value)));
            return this;
        }

        public Builder removeAll(String name) {
            Objects.requireNonNull(name, "name == null");
            String normalizedName = name.trim().toLowerCase();
            headers.remove(normalizedName);
            return this;
        }

        public Headers build() {
            return new Headers(new HashMap<>(headers));
        }
    }
}