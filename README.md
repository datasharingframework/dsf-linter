# DSF Linter - Developer Guide

Linter for DSF (Data Sharing Framework) process plugins. Validates BPMN processes, FHIR resources, and plugin configurations.

## Quick Start

```bash
# 1. Build
mvn clean package

# 2. Run on local JAR
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path your-plugin.jar \
  --html

# 3. View report
# Opens browser at: /tmp/dsf-linter-report-<name>/dsf-linter-report/index.html
```

## Build

```bash
mvn clean package

# Build without tests (faster)
mvn clean package -DskipTests

# Build with verbose output
mvn clean package -X
```

## Usage Examples

### Basic Usage

```bash
# Local JAR file (--mvn NOT needed)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar --path plugin.jar --html

# Local project directory (--mvn REQUIRED)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar --path /path/to/project --mvn clean package --html

# Remote JAR file (--mvn NOT needed)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar --path https://example.com/plugin.jar --html

# Git repository (--mvn REQUIRED)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar --path https://github.com/user/repo.git --mvn clean package --html
```

**Rule of thumb:** If input is NOT a JAR file, use `--mvn`.

### Maven Build Options

**Important:** `--mvn` is **required** for non-JAR inputs (local projects, Git repositories).

**Default Maven Goals** (automatically used):
```bash
-B -q -DskipTests -Dformatter.skip=true -Dexec.skip=true clean package compile dependency:copy-dependencies
```

**How `--mvn` works:**
- Adds goals to defaults (avoiding duplicates)
- Properties with `=` override default values
- Goals without `=` are added if not present

**How `--skip` works:**
- Removes specified goals from defaults

```bash
# Add custom Maven goals (validate, test)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn validate test \
  --html
# Result: mvn -B -q -DskipTests ... clean package compile dependency:copy-dependencies validate test

# Override Maven property
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn -Dformatter.skip=false \
  --html
# Result: mvn -B -q -DskipTests -Dformatter.skip=false ... (overrides default -Dformatter.skip=true)

# Skip specific goals
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn validate \
  --skip clean package \
  --html
# Result: mvn -B -q -DskipTests ... compile dependency:copy-dependencies validate

# Avoid duplicates (clean already in defaults)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn clean validate \
  --html
# Result: mvn -B -q ... clean package compile dependency:copy-dependencies validate (clean not duplicated)
```

### Report Generation

```bash
# HTML and JSON reports
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html --json \
  --report-path ./reports

# Custom report location
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html \
  --report-path /tmp/my-lint-reports
```

### Logging & Output

```bash
# Verbose logging
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html \
  --verbose

# Colored output
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html \
  --color

# No color (useful for CI logs)
NO_COLOR=1 java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html
```

### CI/CD Integration

```bash
# GitHub Actions / GitLab CI
FORCE_COLOR=1 java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html --json \
  --verbose

# Jenkins (fail build on errors)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html
# Exit code: 0 = success, 1 = errors found

# Don't fail build on linter errors (for gradual adoption)
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html \
  --no-fail
```

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
- Maven 3.6+ (recommended for Java 17+)

**IDE Setup:**
- Import as Maven project
- Set JDK to 17 or higher

### Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=BpmnLoadingTest

# Run with verbose output
mvn test -X

# Skip tests during build
mvn clean package -DskipTests
```

### Debugging

```bash
# Debug CLI
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 \
  -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar --html --verbose

# Then attach debugger to localhost:5005
```

### Development Workflow

```bash
# 1. Make changes to linter-core
vim linter-core/src/main/java/dev/dsf/linter/service/BpmnLintingService.java

# 2. Build
mvn clean package -DskipTests

# 3. Test on sample plugin
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path test-plugin.jar \
  --html --verbose

# 4. Check report
open /tmp/dsf-linter-report-test-plugin/dsf-linter-report/index.html

# 5. Run tests
mvn test
```

## Key Components

| Component | Purpose |
|-----------|---------|
| `DsfLinter` | Main orchestrator - coordinates linting phases |
| `ProjectSetupHandler` | Builds projects via Maven |
| `ResourceDiscoveryService` | Discovers plugins and resources |
| `BpmnLintingService` | Validates BPMN processes |
| `FhirLintingService` | Validates FHIR resources |
| `PluginLintingService` | Validates plugin registrations |
| `LintingReportGenerator` | Generates HTML/JSON reports |

## Report Output

### Default Location
```
/tmp/dsf-linter-report-<name>/dsf-linter-report/
├── index.html              # Summary report
├── plugin-name.html        # Individual plugin report
└── plugin-name.json        # JSON report (if --json enabled)
```

### Custom Location
```bash
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html \
  --report-path ./my-reports
```

### Example Output
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

## CLI Options

| Option | Description |
|--------|-------------|
| `--path` | Input path (local dir, Git URL, JAR) |
| `--mvn` | Add Maven goals to default build (see Maven Build Options) |
| `--skip` | Remove Maven goals from default build (see Maven Build Options) |
| `--html` | Generate HTML report |
| `--json` | Generate JSON report |
| `--report-path` | Custom report directory |
| `--verbose` | Verbose logging |
| `--color` | Colored output (respects `NO_COLOR`, `FORCE_COLOR`, `TERM` env vars) |
| `--no-fail` | Exit 0 even on errors |

## Default Maven Goals

When linting non-JAR inputs (projects, Git repos), the following Maven goals are **automatically** used:

| Goal/Property | Purpose |
|---------------|---------|
| `-B` | Non-interactive batch mode |
| `-q` | Quiet mode (warnings and errors only) |
| `-DskipTests` | Skip test execution |
| `-Dformatter.skip=true` | Skip code formatting |
| `-Dexec.skip=true` | Skip exec plugin |
| `clean` | Clean build artifacts |
| `package` | Package the project |
| `compile` | Compile sources |
| `dependency:copy-dependencies` | Copy dependencies for linting |

**Full default command:**
```bash
mvn -B -q -DskipTests -Dformatter.skip=true -Dexec.skip=true clean package compile dependency:copy-dependencies
```

You can:
- **Add** goals with `--mvn` (e.g., `--mvn validate test`)
- **Remove** goals with `--skip` (e.g., `--skip clean`)
- **Override** properties with `--mvn` (e.g., `--mvn -Dformatter.skip=false`)

## Environment Variables

| Variable | Effect |
|----------|--------|
| `NO_COLOR` | Disables colored output (takes precedence) |
| `FORCE_COLOR` | Forces colored output in CI environments |
| `TERM=dumb` | Disables colored output |
| `WT_SESSION` | Windows Terminal detection (auto-enables colors) |
| `ANSICON` | Windows ANSI color support detection |

## Supported Input Types

### 1. Local JAR File
```bash
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/plugin.jar --html
```
✓ No build required, direct linting  
✗ `--mvn` not needed (has no effect)

### 2. Remote JAR URL
```bash
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path https://maven.example.com/plugin-1.0.0.jar --html
```
✓ Downloads and lints the JAR  
✗ `--mvn` not needed (has no effect)

### 3. Local Maven Project
```bash
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/maven-project \
  --mvn clean package --html  # <- REQUIRED
```
✓ Builds project first, then lints  
✓ **`--mvn` is REQUIRED** for local projects

### 4. Git Repository
```bash
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path https://github.com/user/dsf-plugin.git \
  --mvn clean package --html  # <- REQUIRED
```
✓ Clones repository, builds, then lints  
✓ **`--mvn` is REQUIRED** for Git repositories

## Troubleshooting

### Build Failures
```bash
# Check if Maven build works standalone
cd /path/to/project
mvn clean package

# Use custom Maven goals to skip problematic steps
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn clean package -DskipTests -Dformatter.skip=true \
  --html
```

### Missing Dependencies
```bash
# Ensure Maven settings.xml is configured
ls ~/.m2/settings.xml

# Use verbose mode to see what's happening
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html --verbose
```

### Report Not Generated
```bash
# Check report path
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html \
  --report-path $(pwd)/reports

# Verify HTML flag is set
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path plugin.jar \
  --html  # <- Required for HTML output
```

### "No plugins found" Error
```bash
# Error: Missing --mvn for non-JAR input
# The linter will show this error:
# ╔═══════════════════════════════════════════════════════════════
#   ERROR: The linter is primarily designed for JAR files.
#   For project directories or Git repositories, please use
#   the --mvn option to ensure a proper build.
# 
#   Example: dsf-linter --path /path/to/project --mvn clean package
# ╚═══════════════════════════════════════════════════════════════

# Solution: Add --mvn option
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn clean package \
  --html
```

### Understanding --mvn vs --skip

```bash
# Default goals (without --mvn or --skip)
# mvn -B -q -DskipTests -Dformatter.skip=true -Dexec.skip=true clean package compile dependency:copy-dependencies

# Add goals with --mvn
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn validate test \
  --html
# Result: mvn ... clean package compile dependency:copy-dependencies validate test

# Remove goals with --skip
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn validate \
  --skip clean package \
  --html
# Result: mvn ... compile dependency:copy-dependencies validate

# Override property with --mvn
java -jar linter-cli/target/linter-cli-1.0-SNAPSHOT.jar \
  --path /path/to/project \
  --mvn -Dformatter.skip=false \
  --html
# Result: mvn ... -Dformatter.skip=false ... (overrides -Dformatter.skip=true)
```

