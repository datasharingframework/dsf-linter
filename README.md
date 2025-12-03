# DSF Linter - Developer Guide

Linter for DSF (Data Sharing Framework) process plugins. Validates BPMN processes, FHIR resources, and plugin configurations from JAR files.

## Quick Start

```bash
# Build
mvn clean package

# Run on local JAR file
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path your-plugin.jar --html

# Run on remote JAR file
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path https://github.com/datasharingframework/dsf-process-ping-pong/releases/download/v2.0.0.1/dsf-process-ping-pong-2.0.0.1.jar --html

# View report at: /tmp/dsf-linter-report-<name>/dsf-linter-report/index.html
```

## Build

```bash
mvn clean package              # Full build with tests
mvn clean package -DskipTests  # Skip tests (faster)
mvn clean package -X           # Verbose output
```

## Core Concepts

### Input Types

The linter accepts only **JAR files** as input:

| Input Type | Example | Description |
|------------|---------|-------------|
| Local JAR | `--path C:\path\to\plugin.jar` | JAR file in local filesystem |
| Remote JAR | `--path https://example.com/plugin.jar` | JAR file via HTTP/HTTPS URL |

**Note:** Maven projects must first be built with `mvn clean package` before the resulting JAR file can be linted.

### JAR Structure

Expected structure in the JAR file:
- `META-INF/services/` - Plugin registration
- `bpe/` - BPMN process definitions
- `fhir/` - FHIR resources

## Usage Examples

### Basic Linting

```bash
# Local JAR file
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path C:\path\to\plugin.jar --html

# Remote JAR file
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path https://github.com/datasharingframework/dsf-process-ping-pong/releases/download/v2.0.0.1/dsf-process-ping-pong-2.0.0.1.jar --html
```

### Advanced Configuration

```bash
# Multiple reports with custom path
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --json --report-path ./reports

# Verbose output (colors enabled by default, use --no-color to disable)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --verbose

# Lint Maven project
# Step 1: Build project
cd /path/to/project && mvn clean package

# Step 2: Lint resulting JAR
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project/target/my-plugin-1.0.0.jar --html
```

### CI/CD Integration

```bash
# GitHub Actions / GitLab CI
FORCE_COLOR=1 java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --json --verbose

# Jenkins (fail on errors)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html
# Exit code: 0 = success, 1 = errors

# Don't fail build (gradual adoption)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --no-fail
```

## CLI Options

| Option | Description |
|--------|-------------|
| `--path <input>` | Path to JAR file (local or URL) |
| `--html` | Generate HTML report |
| `--json` | Generate JSON report |
| `--report-path <dir>` | Custom report directory |
| `--verbose` | Verbose logging |
| `--no-color` | Disable colored output (default: enabled) |
| `--no-fail` | Exit 0 even on errors |

### Environment Variables

| Variable | Effect |
|----------|--------|
| `NO_COLOR` | Disables colored output |
| `FORCE_COLOR` | Forces colored output (CI) |
| `TERM=dumb` | Disables colored output |
| `WT_SESSION`, `ANSICON` | Windows color detection |

## Project Structure

```
dsf-linter/
├── linter-core/                              # Core linting logic
│   ├── src/main/java/dev/dsf/linter/
│   │   ├── analysis/                         # Resource analysis
│   │   ├── bpmn/                             # BPMN parsing & validation
│   │   ├── fhir/                             # FHIR parsing & validation
│   │   ├── service/                          # Linting services (BPMN, FHIR, Plugin)
│   │   ├── output/                           # Lint item definitions
│   │   │   └── item/                         # Specific lint items
│   │   ├── report/                           # Report generation
│   │   ├── input/                            # Input handling & JAR processing
│   │   ├── setup/                            # Project setup & building
│   │   ├── plugin/                           # Plugin definition discovery
│   │   ├── classloading/                     # Dynamic class loading
│   │   ├── logger/                           # Logging infrastructure
│   │   ├── repo/                             # Repository management
│   │   ├── constants/                        # Constants & configuration
│   │   ├── exception/                        # Custom exceptions
│   │   └── util/                             # Utilities
│   │       ├── api/                          # API version detection
│   │       ├── cache/                        # Caching utilities
│   │       ├── converter/                    # Format converters
│   │       ├── linting/                      # Linting utilities
│   │       ├── loader/                       # Class/service loading
│   │       ├── maven/                        # Maven utilities
│   │       └── resource/                     # Resource management
│   ├── src/main/resources/
│   │   └── templates/                        # HTML report templates
│   └── src/test/
│       ├── java/                             # Unit tests
│       └── resources/                        # Test fixtures
│           ├── bpmn/
│           ├── fhir/
│           └── dsf-multi-plugin-test/
└── linter-cli/                               # CLI interface
    └── src/main/java/dev/dsf/linter/
```

## Development

**Requirements:**
- Java 17+
- Maven 3.6+

**IDE Setup:**
- Import as Maven project
- Set JDK to 17+

### Testing

```bash
mvn test                          # All tests
mvn test -Dtest=BpmnLoadingTest   # Specific test
mvn test -X                       # Verbose
mvn clean package -DskipTests     # Build without tests
```

### Development Workflow

```bash
# 1. Make changes
vim linter-core/src/main/java/dev/dsf/linter/service/BpmnLintingService.java

# 2. Build
mvn clean package -DskipTests

# 3. Test
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path test-plugin.jar --html --verbose

# 4. Check report
open /tmp/dsf-linter-report-test-plugin/dsf-linter-report/index.html

# 5. Run tests
mvn test
```

### Debugging

```bash
# Start with debugger
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 \
  -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --verbose

# Attach debugger to localhost:5005
```

## Key Components

| Component | Purpose |
|-----------|---------|
| `DsfLinter` | Main orchestrator |
| `ProjectSetupHandler` | Maven build execution |
| `ResourceDiscoveryService` | Plugin & resource discovery |
| `BpmnLintingService` | BPMN validation |
| `FhirLintingService` | FHIR validation |
| `PluginLintingService` | Plugin validation |
| `LintingReportGenerator` | Report generation |

## Report Output

### Structure
```
/tmp/dsf-linter-report-<name>/dsf-linter-report/
├── index.html              # Summary
├── plugin-name.html        # Plugin details
└── plugin-name.json        # JSON (if --json)
```

### Example Console Output
```
DSF Linter v1.0.0
=================================================================
Project: /path/to/plugin.jar
Report:  /tmp/dsf-linter-report-plugin/dsf-linter-report
=================================================================

Phase 1: Project Setup
✓ JAR file validated
✓ Resources extracted

Phase 2: Resource Discovery
✓ Found 1 plugin(s)
✓ Plugin: my-process-plugin
  - BPMN: 2 files
  - FHIR: 15 resources

Phase 3: Linting
✓ BPMN validation: 0 errors, 1 warning
✓ FHIR validation: 0 errors, 0 warnings
✓ Plugin validation: 0 errors

Phase 4: Report Generation
✓ HTML report generated

Summary
=================================================================
✓ SUCCESS - No errors found
  Plugins: 1
  Errors: 0
  Warnings: 1
  Execution time: 2.3s
=================================================================
```

## Troubleshooting

### "Input must be a JAR file" Error

The linter accepts only JAR files as input:

```bash
# Wrong - Maven project directly
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project --html

# Correct - Build first, then lint JAR
cd /path/to/project && mvn clean package
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project/target/my-plugin-1.0.0.jar --html
```

### JAR File Not Found

Verify the path:
```bash
# Windows
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path "C:\Users\Username\project\target\plugin.jar" --html

# Linux/Mac
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /home/username/project/target/plugin.jar --html
```

### Missing Dependencies

```bash
# Check Maven settings
ls ~/.m2/settings.xml

# Use verbose mode
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --verbose
```

### Report Not Generated

```bash
# --html flag must be set
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html  # ← Required

# Use absolute path
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --report-path $(pwd)/reports
```

### Remote JAR Download Error

Check the URL and network connection:
```bash
# Test download separately
curl -L -o test.jar https://example.com/plugin.jar

# Then use the local file
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path test.jar --html
```