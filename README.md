# DSF Linter - Developer Guide

Linter for DSF (Data Sharing Framework) process plugins. Validates BPMN processes, FHIR resources, and plugin configurations.

## Quick Start

```bash
# Build
mvn clean package

# Run on JAR file
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path your-plugin.jar --html

# Run on project (requires --mvn)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project --mvn clean package --html

# View report at: /tmp/dsf-linter-report-<name>/dsf-linter-report/index.html
```

## Build

```bash
mvn clean package              # Full build with tests
mvn clean package -DskipTests  # Skip tests (faster)
mvn clean package -X           # Verbose output
```

## Core Concepts

### Input Types & Maven Requirement

| Input Type | Example | Requires --mvn |
|------------|---------|------------|
| Local JAR | `--path plugin.jar` |  No |
| Remote JAR | `--path https://example.com/plugin.jar` |  No |
| Local Project | `--path /path/to/project` |  **Yes** |
| Git Repository | `--path https://github.com/user/repo.git` |  **Yes** |

**Rule:** If input is NOT a JAR file, you MUST use `--mvn`.

### Maven Build Configuration

When `--mvn` is required, these goals run automatically:

```bash
mvn -B -q -DskipTests -Dformatter.skip=true -Dexec.skip=true \
    clean package compile dependency:copy-dependencies
```

You can customize the build in three ways:

**1. Add goals** (with `--mvn`):
```bash
--mvn validate test
# Result: ... clean package compile dependency:copy-dependencies validate test
```

**2. Override properties** (with `--mvn`):
```bash
--mvn -Dformatter.skip=false
# Result: ... -Dformatter.skip=false ... (overrides default)
```

**3. Remove goals** (with `--skip`):
```bash
--mvn validate --skip clean package
# Result: ... compile dependency:copy-dependencies validate
```

## Usage Examples

### Basic Linting

```bash
# JAR file
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html

# Local project
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project --mvn clean package --html

# Git repository
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path https://github.com/user/repo.git --mvn clean package --html
```

### Advanced Configuration

```bash
# Multiple reports with custom location
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --json --report-path ./reports

# Verbose colored output
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --verbose --color

# Custom Maven build
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn validate test -Dformatter.skip=false \
  --skip clean \
  --html
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
| `--path <input>` | Input: JAR, project dir, or Git URL |
| `--mvn <goals>` | Add Maven goals/properties (required for non-JAR) |
| `--skip <goals>` | Remove default Maven goals |
| `--html` | Generate HTML report |
| `--json` | Generate JSON report |
| `--report-path <dir>` | Custom report directory |
| `--verbose` | Verbose logging |
| `--color` | Colored output |
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

### "No plugins found" Error

This happens when linting a project directory without `--mvn`:

```bash
# Wrong
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project --html

# Correct
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project --mvn clean package --html
```

### Build Failures

```bash
# Test Maven build standalone
cd /path/to/project && mvn clean package

# Skip problematic steps
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn -Dformatter.skip=true -DskipTests \
  --html
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
# Ensure --html flag is set
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html  # ← Required

# Use absolute path
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --report-path $(pwd)/reports
```