import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * CSV-driven test runner.
 *
 * Config XML example:
 * <featureFlagTests output="test-output.xml">
 *   <csv path="testdata/featureflagA_basic.csv" />
 *   <csv path="testdata/featureflagA_rollout.csv" />
 * </featureFlagTests>
 *
 * Each CSV must have a header. Required columns:
 * - testId
 * - flagName
 * - flagEnv
 * - flagEnabled
 * - rolloutPercentage
 * - userId
 * - userName
 * - userEnv
 * - userAllowlisted
 * - expectedEnabled   (must be the last column)
 */
public class FeatureFlagServiceTestSuite {

    public static void main(String[] args) throws Exception {
        String configPath = args != null && args.length > 0 ? args[0] : "test-config.xml";
        runFromConfig(configPath);
    }

    public static void runFromConfig(String configPath) throws Exception {
        TestConfig config = readConfig(configPath);
        TestRun run = new TestRun(configPath, config.outputPath);

        for (String csvPath : config.csvPaths) {
            runCsv(csvPath, run);
        }

        writeReport(run, config.outputPath);

        if (run.failed > 0) {
            throw new AssertionError("Test failures: " + run.failed + " (passed=" + run.passed + ")");
        }

        System.out.println("All tests passed. Report: " + config.outputPath);
    }

    private static void runCsv(String csvPath, TestRun run) throws Exception {
        Path path = Paths.get(csvPath);
        if (!Files.exists(path)) {
            run.errors.add("CSV not found: " + csvPath);
            run.failed++;
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                run.errors.add("CSV empty: " + csvPath);
                run.failed++;
                return;
            }

            List<String> header = parseCsvLine(headerLine);
            if (header.isEmpty()) {
                run.errors.add("CSV header missing: " + csvPath);
                run.failed++;
                return;
            }

            if (!"expectedEnabled".equals(header.get(header.size() - 1))) {
                run.errors.add("CSV last column must be expectedEnabled: " + csvPath);
                run.failed++;
                return;
            }

            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                col.put(header.get(i), i);
            }

            String[] required = new String[]{
                    "testId", "flagName", "flagEnv", "flagEnabled", "rolloutPercentage",
                    "userId", "userName", "userEnv", "userAllowlisted", "expectedEnabled"
            };
            for (String r : required) {
                if (!col.containsKey(r)) {
                    run.errors.add("Missing column '" + r + "' in " + csvPath);
                    run.failed++;
                    return;
                }
            }

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() != header.size()) {
                    run.addCase(new TestCaseResult(csvPath, lineNo, "", false, false,
                            "Wrong column count: expected " + header.size() + " got " + fields.size()));
                    run.failed++;
                    continue;
                }

                CsvRow row = new CsvRow(csvPath, lineNo, fields, col);
                TestCaseResult result = executeRow(row);
                run.addCase(result);
                if (result.passed) {
                    run.passed++;
                } else {
                    run.failed++;
                }
            }
        }
    }

    private static TestCaseResult executeRow(CsvRow row) {
        String testId = row.get("testId");
        String flagName = row.get("flagName");

        try {
            String flagEnv = row.get("flagEnv");
            boolean flagEnabled = row.getBoolean("flagEnabled");
            int rolloutPercentage = row.getInt("rolloutPercentage");

            int userId = row.getInt("userId");
            String userName = row.get("userName");
            String userEnv = row.get("userEnv");
            boolean userAllowlisted = row.getBoolean("userAllowlisted");

            boolean expected = row.getBoolean("expectedEnabled");

            FeatureFlagService.addFeatureFlag(flagName, flagEnv, rolloutPercentage, flagEnabled);
            User user = new User(userId, userName, Utils.getEnv(userEnv));

            if (userAllowlisted) {
                FeatureFlagService.addUserToFeature(user, flagName);
            }

            boolean actual = FeatureFlagService.isEnabled(user, flagName);
            boolean passed = actual == expected;
            String message = passed ? "OK" : "Expected " + expected + " but got " + actual;
            return new TestCaseResult(row.csvPath, row.lineNo, testId, passed, actual, message);
        } catch (Exception e) {
            return new TestCaseResult(row.csvPath, row.lineNo, testId, false, false, "Exception: " + e.getMessage());
        }
    }

    private static TestConfig readConfig(String configPath) throws Exception {
        Path path = Paths.get(configPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Config not found: " + configPath);
        }

        Path baseDir = path.toAbsolutePath().getParent();
        if (baseDir == null) {
            baseDir = Paths.get(".").toAbsolutePath().normalize();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(path.toFile());

        Element root = doc.getDocumentElement();
        if (root == null || !"featureFlagTests".equals(root.getTagName())) {
            throw new IllegalArgumentException("Root element must be <featureFlagTests>");
        }

        String output = root.getAttribute("output");
        if (output == null || output.trim().isEmpty()) {
            output = "test-output.xml";
        }
        output = baseDir.resolve(output.trim()).normalize().toString();

        NodeList csvNodes = root.getElementsByTagName("csv");
        List<String> csvPaths = new ArrayList<>();
        for (int i = 0; i < csvNodes.getLength(); i++) {
            Element el = (Element) csvNodes.item(i);
            String p = el.getAttribute("path");
            if (p != null && !p.trim().isEmpty()) {
                csvPaths.add(baseDir.resolve(p.trim()).normalize().toString());
            }
        }

        if (csvPaths.isEmpty()) {
            throw new IllegalArgumentException("No <csv path=\"...\"/> entries in config");
        }

        return new TestConfig(output, csvPaths);
    }

    private static void writeReport(TestRun run, String outputPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = doc.createElement("featureFlagTestReport");
        root.setAttribute("generatedAt", Instant.now().toString());
        root.setAttribute("config", run.configPath);
        root.setAttribute("passed", String.valueOf(run.passed));
        root.setAttribute("failed", String.valueOf(run.failed));
        doc.appendChild(root);

        if (!run.errors.isEmpty()) {
            Element errors = doc.createElement("errors");
            root.appendChild(errors);
            for (String err : run.errors) {
                Element e = doc.createElement("error");
                e.setTextContent(err);
                errors.appendChild(e);
            }
        }

        Element cases = doc.createElement("cases");
        root.appendChild(cases);
        for (TestCaseResult c : run.cases) {
            Element el = doc.createElement("case");
            el.setAttribute("csv", c.csvPath);
            el.setAttribute("line", String.valueOf(c.lineNo));
            el.setAttribute("testId", c.testId == null ? "" : c.testId);
            el.setAttribute("passed", String.valueOf(c.passed));
            el.setAttribute("actualEnabled", String.valueOf(c.actualEnabled));
            el.setTextContent(c.message);
            cases.appendChild(el);
        }

        Path out = Paths.get(outputPath);
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        t.transform(new DOMSource(doc), new StreamResult(new File(outputPath)));
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == ',') {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                } else if (ch == '"') {
                    inQuotes = true;
                } else {
                    cur.append(ch);
                }
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static final class TestConfig {
        final String outputPath;
        final List<String> csvPaths;

        TestConfig(String outputPath, List<String> csvPaths) {
            this.outputPath = outputPath;
            this.csvPaths = csvPaths;
        }
    }

    private static final class TestRun {
        final String configPath;
        final String outputPath;
        int passed = 0;
        int failed = 0;
        final List<TestCaseResult> cases = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        TestRun(String configPath, String outputPath) {
            this.configPath = configPath;
            this.outputPath = outputPath;
        }

        void addCase(TestCaseResult c) {
            cases.add(c);
        }
    }

    private static final class TestCaseResult {
        final String csvPath;
        final int lineNo;
        final String testId;
        final boolean passed;
        final boolean actualEnabled;
        final String message;

        TestCaseResult(String csvPath, int lineNo, String testId, boolean passed, boolean actualEnabled, String message) {
            this.csvPath = csvPath;
            this.lineNo = lineNo;
            this.testId = testId;
            this.passed = passed;
            this.actualEnabled = actualEnabled;
            this.message = message;
        }
    }

    private static final class CsvRow {
        final String csvPath;
        final int lineNo;
        final List<String> fields;
        final Map<String, Integer> col;

        CsvRow(String csvPath, int lineNo, List<String> fields, Map<String, Integer> col) {
            this.csvPath = csvPath;
            this.lineNo = lineNo;
            this.fields = fields;
            this.col = col;
        }

        String get(String name) {
            Integer idx = col.get(name);
            if (idx == null) {
                throw new IllegalArgumentException("Unknown column: " + name);
            }
            return fields.get(idx);
        }

        int getInt(String name) {
            String v = get(name);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid int for " + name + ": " + v);
            }
        }

        boolean getBoolean(String name) {
            String v = get(name);
            if ("true".equalsIgnoreCase(v)) return true;
            if ("false".equalsIgnoreCase(v)) return false;
            throw new IllegalArgumentException("Invalid boolean for " + name + ": " + v);
        }
    }
}
